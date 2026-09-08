package com.tinku.reservas.evento;

import java.util.UUID;

/**
 * {@code reserva.reprogramada} (T-M4-07, US-5, FR-RES-015) — a una Reserva
 * confirmada se le cambió el horario (misma fila, mismo precio, sin transacción
 * nueva). M3 lo escucha (en memoria) para re-agendar los jobs de la Sesión
 * derivada (sala T-5, no-show T+10, corte) al NUEVO horario — sin esto, esos jobs
 * dispararían al horario viejo y el no-show marcaría una Reserva válida.
 *
 * No figura en ningún Spec; es un mecanismo interno M4→M3 (AGENTS §4).
 */
public class ReservaReprogramadaEvent extends ReservaEvento {

    public ReservaReprogramadaEvent(Object source, UUID reservaId) {
        super(source, "reserva.reprogramada", reservaId);
    }
}