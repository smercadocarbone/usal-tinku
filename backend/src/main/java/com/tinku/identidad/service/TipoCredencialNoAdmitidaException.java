package com.tinku.identidad.service;

/**
 * Tipo de credencial que no se admite con el estado actual del flag de ADR-M1-06: apagado, solo los
 * tipos de BR-ID-01; encendido, solo los nuevos. Mismo 400 y mensaje que un valor desconocido.
 */
public class TipoCredencialNoAdmitidaException extends RuntimeException {
    public TipoCredencialNoAdmitidaException() {
        super("El cuerpo de la solicitud no es válido.");
    }
}
