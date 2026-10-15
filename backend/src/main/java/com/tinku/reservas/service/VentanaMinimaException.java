package com.tinku.reservas.service;

/** No se reserva con menos de 21 minutos de anticipación (FR-RES-013, Tabla_Tiempos). */
public class VentanaMinimaException extends RuntimeException {
    public VentanaMinimaException(String mensaje) {
        super(mensaje);
    }
}