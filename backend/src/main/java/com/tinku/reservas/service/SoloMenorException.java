package com.tinku.reservas.service;

/** Acción restringida a la cuenta propia del menor (Artículo II — el menor solo solicita). */
public class SoloMenorException extends RuntimeException {
    public SoloMenorException(String mensaje) {
        super(mensaje);
    }
}