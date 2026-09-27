package com.tinku.reservas.service;

import java.time.Instant;
import java.util.List;

/**
 * ADR-M5-03: el paquete no se puede armar (el Tutor no lo ofrece, o alguna de las 4 fechas no
 * entra) o no se puede cancelar entero. Se traduce a 422; {@code fechas} lista las que chocan.
 */
public class PaqueteNoDisponibleException extends RuntimeException {

    private final List<Instant> fechas;

    public PaqueteNoDisponibleException(String mensaje) {
        this(mensaje, List.of());
    }

    public PaqueteNoDisponibleException(String mensaje, List<Instant> fechas) {
        super(mensaje);
        this.fechas = List.copyOf(fechas);
    }

    public List<Instant> getFechas() {
        return fechas;
    }
}
