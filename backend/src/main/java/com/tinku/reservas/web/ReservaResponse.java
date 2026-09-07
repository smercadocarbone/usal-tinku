package com.tinku.reservas.web;

import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.model.Reserva;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservaResponse(UUID id, UUID pagadorId, UUID beneficiarioId, UUID tutorId,
                              Instant horario, BigDecimal precio, EstadoReserva estado,
                              MotivoCancelacion motivoCancelacion) {

    public static ReservaResponse from(Reserva r) {
        return new ReservaResponse(r.getId(), r.getPagador().getId(), r.getBeneficiario().getId(),
                r.getTutor().getId(), r.getHorario(), r.getPrecio(), r.getEstado(),
                r.getMotivoCancelacion());
    }
}