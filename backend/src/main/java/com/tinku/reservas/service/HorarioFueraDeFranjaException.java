package com.tinku.reservas.service;

/** La franja de disponibilidad no aplica al día/fecha y hora propuestos (FR-RES-012). */
public class HorarioFueraDeFranjaException extends RuntimeException {
    public HorarioFueraDeFranjaException(String mensaje) {
        super(mensaje);
    }
}