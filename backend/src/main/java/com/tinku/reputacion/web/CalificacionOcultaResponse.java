package com.tinku.reputacion.web;

import com.tinku.reputacion.model.Calificacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Calificacion oculta (tutor_a_estudiante) del panel de Moderacion (T-M7-04).
 * Nunca expone comentario — las ocultas no tienen uno — ni datos sensibles del
 * Tutor detrás de su autor. Solo para el admin de moderación (AdminModeracionGate).
 */
public record CalificacionOcultaResponse(UUID id, UUID sesionId, UUID autorId, Short estrellas,
                                         Instant createdAt) {

    public static CalificacionOcultaResponse from(Calificacion c) {
        return new CalificacionOcultaResponse(c.getId(), c.getSesionId(), c.getAutorId(),
                c.getEstrellas(), c.getCreatedAt());
    }
}