package com.tinku.admin.web;

import com.tinku.admin.model.EstadoTicket;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.model.TicketSoporte;

import java.time.Instant;
import java.util.UUID;

/** Vista de un ticket de soporte (US-7) para el canal público y la cola del rol. */
public record TicketResponse(
        UUID id,
        UUID usuarioId,
        String origenModulo,
        String asunto,
        String detalle,
        EstadoTicket estado,
        RolAdmin rolAsignado,
        Instant creadoEn,
        Instant resueltoEn) {

    public static TicketResponse from(TicketSoporte t) {
        // getUsuario() es LAZY pero getId() sale de la FK — no dispara carga.
        return new TicketResponse(t.getId(), t.getUsuario().getId(), t.getOrigenModulo(),
                t.getAsunto(), t.getDetalle(), t.getEstado(), t.getRolAsignado(),
                t.getCreadoEn(), t.getResueltoEn());
    }
}