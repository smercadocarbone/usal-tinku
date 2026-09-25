package com.tinku.identidad.service;

/** FR-ID-020 / Art. II: quien no es Adulto Responsable (o es un menor) no da de alta menores → 403. */
public class AltaMenorNoPermitidaException extends RuntimeException {
    public AltaMenorNoPermitidaException() {
        super("Solo un Adulto Responsable puede dar de alta la cuenta de un menor.");
    }
}
