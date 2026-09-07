package com.tinku.reservas.service;

/**
 * No se pudo resolver la tarifa del Tutor porque M5 (Spec M5 US-6) todavía no
 * existe. Es independiente de quién el pagador: el precio lo fija el Tutor, no
 * quien reserva — por eso no puede ir en el request.
 */
public class TarifaNoConfiguradaException extends RuntimeException {
    public TarifaNoConfiguradaException(String mensaje) {
        super(mensaje);
    }
}