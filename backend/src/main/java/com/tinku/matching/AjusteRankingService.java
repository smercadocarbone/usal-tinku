package com.tinku.matching;

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
 *  - Suma las señales implícitas de la consulta FR-MATCH-003 al score semántico
 *    y reordena descendente. La ponderación exacta es ADR-M2-02 (se resuelve
 *    empíricamente en el piloto, no con un número fijo de entrada).
 *
 * Mientras el {@link ReputacionSignalProviderStub} esté activo esto es una
 * identidad ordenada — el camino de la consulta a M7 ya queda cableado.
 */
@Service
public class AjusteRankingService {

    private final ReputacionSignalProvider reputacion;

    public AjusteRankingService(ReputacionSignalProvider reputacion) {
        this.reputacion = reputacion;
    }

    public List<ResultadoRanking> ajustar(List<ResultadoRanking> rankingSemantico) {
        if (rankingSemantico.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = rankingSemantico.stream().map(ResultadoRanking::tutorId).toList();
        Set<UUID> sombra = reputacion.tutoresEnSombraBrMatch01(ids);
        var senales = reputacion.senalesImplicitas(ids);

        List<ResultadoRanking> resultado = new ArrayList<>(rankingSemantico.size());
        for (ResultadoRanking item : rankingSemantico) {
            if (sombra.contains(item.tutorId())) {
                continue; // BR-MATCH-01: excluido mientras dure la sombra.
            }
            double ajustado = item.score() + senales.getOrDefault(item.tutorId(), 0.0);
            resultado.add(new ResultadoRanking(item.tutorId(), ajustado, item.noAutorizado()));
        }

        resultado.sort(Comparator.comparingDouble(ResultadoRanking::score).reversed());
        return List.copyOf(resultado);
    }
}