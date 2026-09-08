package com.tinku.reservas.evento;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Base de los eventos de dominio de Reserva emitidos por M4 (Artículo IX, AGENTS
 * §4). El {@code nombre} es el contrato público de cada evento (ej. {@code
 * reserva.cancelada}) — NO se renombra: M3 lo escucha por tipo en memoria y M5-B
 * registrará su listener por este nombre. Hoy ninguno de los dos está en un Spec,
 * son mecanismos internos (M4→M5 manual, M4→M3 cancelación/reprogramación de la
 * Sesión derivada); ambos quedan documentados en la sección 2 de Spec_M5.
 */
public abstract class ReservaEvento extends ApplicationEvent {

    private final String nombre;
    private final UUID reservaId;

    protected ReservaEvento(Object source, String nombre, UUID reservaId) {
        super(source);
        this.nombre = nombre;
        this.reservaId = reservaId;
    }

    public String getNombre() {
        return nombre;
    }

    public UUID getReservaId() {
        return reservaId;
    }
}