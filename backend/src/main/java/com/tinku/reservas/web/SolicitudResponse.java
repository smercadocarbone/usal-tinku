package com.tinku.reservas.web;

import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.SolicitudSesion;

import java.time.Instant;
import java.util.UUID;

public record SolicitudResponse(UUID id, UUID tutorId, Instant horarioPropuesto,
                                EstadoSolicitud estado, Instant expiraAt) {

    public static SolicitudResponse from(SolicitudSesion s) {
        return new SolicitudResponse(s.getId(), s.getTutor().getId(), s.getHorarioPropuesto(),
                s.getEstado(), s.getExpiraAt());
    }
}