package com.tinku.pagos.web;

import com.tinku.pagos.model.TarifaTutor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tarifa por sesión del Tutor (US-6 / M5-H) — lo que el Tutor cobra por sesión. */
public record TarifaTutorResponse(UUID tutorId, BigDecimal precioSesion, Instant updatedAt) {

    public static TarifaTutorResponse from(TarifaTutor t) {
        return new TarifaTutorResponse(t.getTutorId(), t.getPrecioSesion(), t.getUpdatedAt());
    }
}