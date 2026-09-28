package com.tinku.pagos.web;

import com.tinku.pagos.model.TarifaTutor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarifa del Tutor (US-6 / M5-H) — lo que cobra POR HORA de clase (D6). Suma el
 * {@code pisoHora} vigente (T06) para que el frontend valide antes de enviar y
 * avise si una tarifa vieja quedó por debajo (PT4). {@code precioHora} es null si
 * el Tutor todavía no la configuró. {@code comisionPorcentaje} (FR-PAG-019) es la comisión
 * de Tinku y {@code comisionMercadoPagoPorcentaje} la tasa estimada de MercadoPago (ADR-M5-04),
 * para que el Tutor vea cuánto le queda; solo las ve él (Art. III). {@code paquete*}
 * (v2.5, ADR-M5-03): si ofrece el paquete mensual y con qué descuento.
 */
public record TarifaTutorResponse(UUID tutorId, BigDecimal precioHora, Instant updatedAt, BigDecimal pisoHora,
                                  int comisionPorcentaje, BigDecimal comisionMercadoPagoPorcentaje,
                                  boolean paqueteHabilitado, int paqueteDescuentoPorcentaje) {

    public static TarifaTutorResponse from(TarifaTutor t, BigDecimal pisoHora, int comisionPorcentaje,
                                           BigDecimal comisionMercadoPagoPorcentaje) {
        return new TarifaTutorResponse(t.getTutorId(), t.getPrecioHora(), t.getUpdatedAt(), pisoHora,
                comisionPorcentaje, comisionMercadoPagoPorcentaje, t.isPaqueteHabilitado(),
                t.getPaqueteDescuentoPorcentaje());
    }

    public static TarifaTutorResponse sinTarifa(UUID tutorId, BigDecimal pisoHora, int comisionPorcentaje,
                                                BigDecimal comisionMercadoPagoPorcentaje) {
        return new TarifaTutorResponse(tutorId, null, null, pisoHora, comisionPorcentaje,
                comisionMercadoPagoPorcentaje, false, 0);
    }
}