package com.tinku.reservas.service;

/** R-D D-3 (T2-H2): franja puntual con una fecha anterior a hoy (zona de negocio). */
public class FechaFranjaPasadaException extends RuntimeException {
    public FechaFranjaPasadaException() {
        super("La fecha de la franja ya pasó.");
    }
}
