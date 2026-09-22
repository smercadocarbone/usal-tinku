package com.tinku.identidad.service;

/**
 * AUD-033: solo una credencial PENDIENTE se aprueba o rechaza. Sin este guard se
 * podía aprobar una ya rechazada, o re-rechazar el intento 3 y duplicar el backoff
 * (FR-ID-012). Se traduce a 422.
 */
public class CredencialNoPendienteException extends RuntimeException {
    public CredencialNoPendienteException() {
        super("La credencial no está pendiente de revisión.");
    }
}
