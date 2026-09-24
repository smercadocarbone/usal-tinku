package com.tinku.identidad.port;

import java.util.UUID;

/**
 * Puerto hacia el módulo de Reservas (M4) para la baja confirmada de un menor
 * (FR-ID-014): cancela sus reservas futuras por la vía normal de cancelación, así
 * M5 resuelve reembolso o liberación con FR-RES-008 y M3 desagenda la Sesión.
 *
 * Implementación real: {@code com.tinku.reservas.port.CancelacionReservasFuturasReal}.
 */
@FunctionalInterface
public interface CancelacionReservasFuturas {

    /** Cancela las reservas futuras del menor en nombre de su Adulto Responsable.
     *  Devuelve cuántas canceló. */
    int cancelarFuturasDeMenor(UUID menorId, UUID adultoResponsableId);
}
