package com.tinku.identidad.service;

/** Solo se revisa un CAP pendiente o en revisión legal (409). */
public class CapNoRevisableException extends RuntimeException {
    public CapNoRevisableException() {
        super("El certificado ya fue revisado.");
    }
}
