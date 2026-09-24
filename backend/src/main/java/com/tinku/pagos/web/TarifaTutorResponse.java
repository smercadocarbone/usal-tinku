package com.tinku.pagos.web;

import com.tinku.pagos.model.TarifaTutor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tarifa del Tutor (US-6 / M5-H) — lo que cobra POR HORA de clase (D6). */
public record TarifaTutorResponse(UUID tutorId, BigDecimal precioHora, Instant updatedAt) {

    public static TarifaTutorResponse from(TarifaTutor t) {
        return new TarifaTutorResponse(t.getTutorId(), t.getPrecioHora(), t.getUpdatedAt());
    }
}