package com.tinku.reservas.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Qué pasa con la plata cuando se cancela una Reserva confirmada (FR-RES-008,
 * Tabla_Tiempos_Tinku.md "Cancelación sin penalidad: 24 hs antes"). Única
 * definición: la aplica M5 al cancelar ({@code EscrowService.onReservaCancelada})
 * y la muestra la UI ANTES de que el usuario confirme (UX-05 §4), así nunca
 * divergen.
 *
 * <ul>
 *   <li>Con 24 hs o más de anticipación → reembolso total.</li>
 *   <li>Si cancela el Tutor (no el pagador) → reembolso total, siempre.</li>
 *   <li>Si cancela el pagador con menos de 24 hs → se le paga al Tutor.</li>
 * </ul>
 */
public final class PoliticaCancelacion {

    public static final Duration VENTANA_SIN_PENALIDAD = Duration.ofHours(24);

    private PoliticaCancelacion() {
    }

    public static boolean reembolsoTotal(Reserva reserva, UUID canceladaPorUsuarioId, Instant ahora) {
        boolean conMargen = !ahora.plus(VENTANA_SIN_PENALIDAD).isAfter(reserva.getHorario());
        boolean canceloElPagador = reserva.getPagador() != null
                && reserva.getPagador().getId().equals(canceladaPorUsuarioId);
        return conMargen || !canceloElPagador;
    }
}
