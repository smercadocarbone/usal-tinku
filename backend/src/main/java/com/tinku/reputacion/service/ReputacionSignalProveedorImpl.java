package com.tinku.reputacion.service;

import com.tinku.matching.ReputacionSignalProvider;
import com.tinku.reputacion.model.SenalesImplicitasTutor;
import com.tinku.reputacion.repository.CalificacionRepository;
import com.tinku.reputacion.repository.SenalesImplicitasTutorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementacion real de {@link ReputacionSignalProvider} (reemplaza el stub de
 * M2-C). Peso simple por Tutor (ADR-M7-01 / ADR-M2-02, version piloto) — se
 * recalibra con datos reales:
 *
 * <ul>
 *   <li>volumen: hasta 1.0 por sesiones dictadas (saturado en 50 sesiones)</li>
 *   <li>recontratacion: hasta 0.5 por tasa de re-enganche</li>
 *   <li>puntualidad: +-0.1 alrededor de la media de puntualidad</li>
 *   <li>cancelacion/no-show: hasta -0.8 (penalizacion fuerte)</li>
 * </ul>
 *
 * {@code tutoresEnSombraBrMatch01}: Tutores con calificacion publica de 1-2
 * estrellas en las ultimas 24hs (BR-MATCH-01) — interseptado contra los
 * candidatos del motor de matching.
 */
@Service
public class ReputacionSignalProveedorImpl implements ReputacionSignalProvider {

    /** BR-MATCH-01 — ventana de la sombra: 24hs. Tabla_Tiempos fila "Sombra BR-MATCH-01". */
    private static final Duration VENTANA_SOMBRA = Duration.ofHours(24);

    private final SenalesImplicitasTutorRepository senalesRepo;
    private final CalificacionRepository calificacionRepo;

    public ReputacionSignalProveedorImpl(SenalesImplicitasTutorRepository senalesRepo,
                                         CalificacionRepository calificacionRepo) {
        this.senalesRepo = senalesRepo;
        this.calificacionRepo = calificacionRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Double> senalesImplicitas(Collection<UUID> tutorIds) {
        Map<UUID, Double> pesos = new HashMap<>();
        senalesRepo.findByTutorIdIn(tutorIds).forEach(s -> pesos.put(s.getTutorId(), peso(s)));
        return pesos;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> tutoresEnSombraBrMatch01(Collection<UUID> tutorIds) {
        Set<UUID> sombra = new HashSet<>(
                calificacionRepo.findTutoresEnSombraBrMatch01(Instant.now().minus(VENTANA_SOMBRA)));
        sombra.retainAll(tutorIds);
        return sombra;
    }

    private static double peso(SenalesImplicitasTutor s) {
        double volumen = Math.min(s.getSesionesDictadasTotal(), 50) * 0.02;
        double recontratacion = s.getTasaRecontratacion().doubleValue() * 0.5;
        double puntualidad = (s.getPuntualidadPromedio().doubleValue() - 0.5) * 0.2;
        double cancelacion = -s.getTasaCancelacionNoshow().doubleValue() * 0.8;
        return Math.max(0.0, volumen + recontratacion + puntualidad + cancelacion);
    }
}