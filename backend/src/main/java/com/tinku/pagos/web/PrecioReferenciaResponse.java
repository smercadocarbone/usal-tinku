package com.tinku.pagos.web;

import com.tinku.pagos.model.PrecioReferenciaRegional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Respuesta de {@code GET /api/pagos/precio-referencia/{provincia}} (T-M5-09,
 * US-6): la versión vigente de la sugerencia. Es el SNAPSHOT que el cliente
 * copia al perfil del Tutor — no hay FK a la fila de configuración (FR-PAG-005),
 * y una versión nueva (revisión trimestral de M8) no altera lo ya fijado
 * (FR-PAG-006).
 */
public record PrecioReferenciaResponse(String provincia, BigDecimal valorSugerido,
                                       Integer version, Instant vigenteDesde) {

    public static PrecioReferenciaResponse from(PrecioReferenciaRegional precio) {
        return new PrecioReferenciaResponse(
                precio.getProvincia(), precio.getValorSugerido(),
                precio.getVersion(), precio.getVigenteDesde());
    }
}