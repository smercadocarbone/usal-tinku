package com.tinku.reputacion.service;

import com.tinku.identidad.dto.ReputacionTutor;
import com.tinku.identidad.port.ReputacionPerfilProvider;
import com.tinku.reputacion.repository.CalificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Implementacion real de {@link ReputacionPerfilProvider} (reemplaza el stub de
 * M1). FR-REP-007: el promedio publico solo se muestra con 5 o mas
 * calificaciones; el conteo siempre se devuelve.
 */
@Service
public class ReputacionPerfilProveedor implements ReputacionPerfilProvider {

    /** FR-REP-007 — umbral minimo para exponer un promedio publico. */
    private static final long UMBRAL_PUBLICO = 5;

    private final CalificacionRepository calificacionRepo;

    public ReputacionPerfilProveedor(CalificacionRepository calificacionRepo) {
        this.calificacionRepo = calificacionRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public ReputacionTutor reputacion(UUID tutorId) {
        long count = calificacionRepo.countPublicasPorTutor(tutorId);
        if (count < UMBRAL_PUBLICO) {
            return new ReputacionTutor(null, count);
        }
        BigDecimal promedio = calificacionRepo.promedioPublicoPorTutor(tutorId)
                .map(BigDecimal::valueOf)
                .map(p -> p.setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
        return new ReputacionTutor(promedio, count);
    }
}