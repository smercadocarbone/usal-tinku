package com.tinku.identidad.service;

/**
 * FR-ID-014: el menor tiene reservas futuras y la baja no viene confirmada.
 * Expone el conteo para que el cliente pida confirmación explícita.
 */
public class ReservasFuturasPendientesException extends RuntimeException {

    private final long cantidadReservas;

    public ReservasFuturasPendientesException(long cantidadReservas) {
        super("El menor tiene " + cantidadReservas
                + " reserva(s) futura(s). Confirmá la baja para cancelarlas.");
        this.cantidadReservas = cantidadReservas;
    }

    public long getCantidadReservas() {
        return cantidadReservas;
    }
}
