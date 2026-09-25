package com.tinku.reservas.service;

/** T09: el adicional de resumen no se puede contratar para esta Reserva → 422. */
public class AdicionalResumenNoDisponibleException extends RuntimeException {
    public AdicionalResumenNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
