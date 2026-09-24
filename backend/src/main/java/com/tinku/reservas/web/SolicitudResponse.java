package com.tinku.reservas.web;

import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.SolicitudSesion;

import java.time.Instant;
import java.util.UUID;

/**
 * Solicitud de Sesión de un menor (US-2/US-3). UX-05 §5: suma el nombre del menor
 * y del Tutor para que el Adulto Responsable entienda el pedido sin abrir nada
 * más (solo nombre y apellido: nunca DNI ni email). Se arma dentro de una
 * transacción: menor y tutor son asociaciones perezosas.
 */
public record SolicitudResponse(UUID id, UUID tutorId, Instant horarioPropuesto,
                                EstadoSolicitud estado, Instant expiraAt,
                                UUID menorId, String menorNombre,
                                String tutorNombre, String tutorApellido) {

    public static SolicitudResponse from(SolicitudSesion s) {
        return new SolicitudResponse(s.getId(), s.getTutor().getId(), s.getHorarioPropuesto(),
                s.getEstado(), s.getExpiraAt(),
                s.getMenor().getId(), s.getMenor().getNombre(),
                s.getTutor().getNombre(), s.getTutor().getApellido());
    }
}