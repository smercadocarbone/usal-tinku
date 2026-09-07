package com.tinku.identidad.service;

/** La credencial referenciada no existe (o no pertenece al Tutor que la opera). */
public class CredencialNoEncontradaException extends RuntimeException {
    public CredencialNoEncontradaException() {
        super("La credencial solicitada no existe.");
    }
}
