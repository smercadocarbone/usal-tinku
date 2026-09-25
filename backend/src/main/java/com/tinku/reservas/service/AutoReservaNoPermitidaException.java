package com.tinku.reservas.service;

/**
 * Revisión por rol, punto 1: el Tutor no puede ser el pagador ni el beneficiario de su propia
 * clase, ni el Adulto Responsable del menor que la toma (Art. II: siempre un adulto independiente) → 422.
 */
public class AutoReservaNoPermitidaException extends RuntimeException {
    public AutoReservaNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
