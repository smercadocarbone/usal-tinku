package com.tinku.identidad.service;

/** ADR-M1-10: la firma digital solo se verifica sobre el PDF original, no sobre una foto. */
public class CapDebeSerPdfException extends RuntimeException {
    public CapDebeSerPdfException() {
        super("Subí el PDF original que descargaste del Registro Nacional de Reincidencia: una foto o captura no tiene la firma digital.");
    }
}
