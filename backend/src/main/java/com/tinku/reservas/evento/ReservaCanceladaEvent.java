package com.tinku.reservas.evento;

import java.util.UUID;

/**
 * {@code reserva.cancelada} (T-M4-08, US-6/US-7, FR-RES-004/008/017) — una
 * Reserva confirmada (pagada) se canceló manualmente. Quién lo disparó decide la
 * asimetría, y esa decisión es de M5: este evento solo lleva {@code reservaId} y
 * {@code canceladaPorUsuarioId} (el pagador o el Tutor), y el listener en M5
 * relee la Reserva (estado, precio, horario) para ejecutar reembolso o liberación
 * del escrow según FR-RES-008. Las reservas en {@code pendiente_pago} se cancelan
 * sin evento (FR-RES-017: no hay nada que cobrar ni reembolsar).
 *
 * M3 también lo escucha (en memoria) para desagendar los jobs de la Sesión
 * derivada; su listener es solo limpieza — los jobs ya son no-op si la Reserva
 * dejó de estar confirmada.
 *
 * No figura en ningún Spec; se documenta en la sección 2 de Spec_M5 (AGENTS §4).
 */
public class ReservaCanceladaEvent extends ReservaEvento {

    private final UUID canceladaPorUsuarioId;

    public ReservaCanceladaEvent(Object source, UUID reservaId, UUID canceladaPorUsuarioId) {
        super(source, "reserva.cancelada", reservaId);
        this.canceladaPorUsuarioId = canceladaPorUsuarioId;
    }

    public UUID getCanceladaPorUsuarioId() {
        return canceladaPorUsuarioId;
    }
}