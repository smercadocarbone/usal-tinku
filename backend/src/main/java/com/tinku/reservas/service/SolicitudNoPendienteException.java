package com.tinku.reservas.service;

/** La Solicitud de Sesión no existe (o ya dejó de estar pendiente). */
public class SolicitudNoPendienteException extends RuntimeException {
    public SolicitudNoPendienteException() {
        super("Ese pedido de clase ya no está pendiente.");
    }
}