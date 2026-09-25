package com.tinku.identidad.service;

/** El Certificado de Antecedentes Penales referenciado no existe. */
public class CapNoEncontradoException extends RuntimeException {
    public CapNoEncontradoException() {
        super("El certificado de antecedentes penales no existe.");
    }
}
