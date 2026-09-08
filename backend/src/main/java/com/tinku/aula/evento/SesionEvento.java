package com.tinku.aula.evento;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Base de los eventos de dominio de sesión emitidos por M3 hacia M5 (Artículo IX,
 * AGENTS §4). El nombre es EXACTAMENTE el del Spec/Plan (ej. {@code sesion.finalizada}),
 * no se renombra: M5-B registra listeners por ese contrato. Los payloads mínimos
 * (sesionId + reservaId) permiten a cada listener re-leer el resto desde el repo.
 */
public abstract class SesionEvento extends ApplicationEvent {

    private final String nombre;
    private final UUID sesionId;
    private final UUID reservaId;

    protected SesionEvento(Object source, String nombre, UUID sesionId, UUID reservaId) {
        super(source);
        this.nombre = nombre;
        this.sesionId = sesionId;
        this.reservaId = reservaId;
    }

    public String getNombre() {
        return nombre;
    }

    public UUID getSesionId() {
        return sesionId;
    }

    public UUID getReservaId() {
        return reservaId;
    }
}