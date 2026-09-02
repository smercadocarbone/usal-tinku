package com.tinku.identidad.service;

/** El nombre/apellido declarado no coincide con lo extraído del documento. */
public class DocumentoNoCoincideException extends RuntimeException {
    public DocumentoNoCoincideException() {
        super("No pudimos verificar tu documento.");
    }
}
