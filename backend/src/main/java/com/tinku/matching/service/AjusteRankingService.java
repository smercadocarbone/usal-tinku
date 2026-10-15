package com.tinku.matching.service;

import com.tinku.matching.model.ResultadoRanking;
import com.tinku.matching.port.ReputacionSignalProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * T-M2-07: reordenamiento final del ranking en Java, después del cálculo
 * semántico (Plan_M2, sección 3, paso 6). Son reglas de negocio de M7
 * (reputación) — por eso NO viven en el servicio Python.
 *
 *  - Excluye a los Tutores en sombra de BR-MATCH-01 (1-2 estrellas recientes).
 *  - US-1: con texto libre, descarta los resultados por debajo del puntaje mínimo de
 *    relevancia: mejor "no encontramos" que tutores que no tienen nada que ver.
 *  - FR-MATCH-003 / ADR-M2-02: las señales implícitas solo DESEMPATAN. Se normalizan a
 *    [0, 1] y pesan como mucho {@code tinku.matching.peso-reputacion} (0,05): la
 *    reputación nunca le gana a una diferencia real de relevancia.
 */
@Service
public class AjusteRankingService {

    /** Techo del peso crudo de M7 (volumen 1,0 + recontratación 0,5 + puntualidad 0,1). */
    static final double SENAL_MAXIMA = 1.6;

    private final ReputacionSignalProvider reputacion;
    private final double pesoReputacion;
    private final double scoreMinimo;
    private final double margenRelativo;

    public AjusteRankingService(ReputacionSignalProvider reputacion,
                                @Value("${tinku.matching.peso-reputacion:0.05}") double pesoReputacion,
                                @Value("${tinku.matching.score-minimo:0.46}") double scoreMinimo,
                                @Value("${tinku.matching.margen-relativo:0.25}") double margenRelativo) {
        this.reputacion = reputacion;
        this.pesoReputacion = pesoReputacion;
        this.scoreMinimo = scoreMinimo;
        this.margenRelativo = margenRelativo;
    }

    /** Con texto libre: exige el puntaje mínimo de relevancia. */
    public List<ResultadoRanking> ajustar(List<ResultadoRanking> rankingSemantico) {
        return ajustar(rankingSemantico, true);
    }

    /**
     * @param exigirRelevancia {@code false} cuando la búsqueda es solo por filtros de catálogo
     *                         (materia/tema): ahí los candidatos ya cumplen el filtro y el texto
     *                         semántico es solo el nombre de la materia.
     */
    public List<ResultadoRanking> ajustar(List<ResultadoRanking> rankingSemantico, boolean exigirRelevancia) {
        if (rankingSemantico.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = rankingSemantico.stream().map(ResultadoRanking::tutorId).toList();
        Set<UUID> sombra = reputacion.tutoresEnSombraBrMatch01(ids);
        var senales = reputacion.senalesImplicitas(ids);

        // R-E E-1 (T1-M1, D-3): además del mínimo absoluto, un corte relativo al mejor resultado.
        // Calibrado en docs/operacion/CALIBRACION_busqueda.md: con el modelo de embeddings, un texto
        // sin sentido o una consulta ajena llegan a 0,44-0,46 contra cualquier tutor; los relevantes, a 0,75 de mediana.
        double mejor = rankingSemantico.stream().filter(r -> !sombra.contains(r.tutorId()))
                .mapToDouble(ResultadoRanking::score).max().orElse(0.0);
        double corte = Math.max(scoreMinimo, mejor - margenRelativo);

        List<ResultadoRanking> resultado = new ArrayList<>(rankingSemantico.size());
        for (ResultadoRanking item : rankingSemantico) {
            if (sombra.contains(item.tutorId())) {
                continue; // BR-MATCH-01: excluido mientras dure la sombra.
            }
            if (exigirRelevancia && item.score() < corte) {
                continue; // US-1: no se fuerzan resultados de baja calidad.
            }
            double senal = Math.clamp(senales.getOrDefault(item.tutorId(), 0.0), 0.0, SENAL_MAXIMA);
            double ajustado = item.score() + pesoReputacion * senal / SENAL_MAXIMA;
            resultado.add(new ResultadoRanking(item.tutorId(), ajustado, item.noAutorizado()));
        }

        resultado.sort(Comparator.comparingDouble(ResultadoRanking::score).reversed());
        return List.copyOf(resultado);
    }
}
