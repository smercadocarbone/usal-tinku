package com.tinku.reservas.evento;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Evento de dominio en memoria (Artículo IX) — la Reserva pasó a {code confirmada}
 * y el pago quedó registrado. Lo publica ReservaService al confirmar (hoy el stub
 * {@code confirmarPagoSimulado}; el webhook real de MercadoPago, M5-B, emitirá el
 * mismo evento), y M3 lo escucha para crear la Sesión de Aprendizaje y programar
 * los jobs de sala/no-show/corte (T-M3-03/04/05).
 *
 * Este evento NO figura en ningún Spec: es un mecanismo interno M4→M3 (sin broker,
 * AGENTS §4). No lo consumen listeners externos aún; M5 no escucha estos eventos.
 */
public class ReservaConfirmadaEvent extends ApplicationEvent {

    private final UUID reservaId;

    public ReservaConfirmadaEvent(Object source, UUID reservaId) {
        super(source);
        this.reservaId = reservaId;
    }

    public UUID getReservaId() {
        return reservaId;
    }
}