package com.tinku.reputacion.web;

import com.tinku.reputacion.model.Calificacion;

import java.time.Instant;
import java.util.UUID;

public record CalificacionResponse(UUID id, UUID sesionId, String direccion, Short estrellas,
                                   String comentario, Instant editableHasta, Instant createdAt) {

    public static CalificacionResponse from(Calificacion c) {
        return new CalificacionResponse(c.getId(), c.getSesionId(), c.getDireccion(),
                c.getEstrellas(), c.getComentario(), c.getEditableHasta(), c.getCreatedAt());
    }
}