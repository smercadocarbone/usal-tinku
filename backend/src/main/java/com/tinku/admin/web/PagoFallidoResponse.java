package com.tinku.admin.web;

import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la cola de intervención manual (US-5, FR-ADM-004/FR-PAG-007): pagos
 * {@code retenido_escrow} que agotaron sus 3 reintentos de liberación. Expone el
 * historial de intentos para que el Admin de Soporte Financiero decida.
 */
public record PagoFallidoResponse(
        UUID id,
        UUID reservaId,
        BigDecimal montoBruto,
        EstadoTransaccion estado,
        int intentosLiberacion,
        Instant liberarAt,
        Instant createdAt) {

    public static PagoFallidoResponse from(Transaccion t) {
        return new PagoFallidoResponse(t.getId(), t.getReservaId(), t.getMontoBruto(),
                t.getEstado(), t.getIntentosLiberacion(), t.getLiberarAt(), t.getCreatedAt());
    }
}