package com.tinku.matching;

/** Un id de tema no es un UUID (o el body del PUT viene malformado):
 * 422 sin distinguir el formato interno. */
public class TemaIdMalformadoException extends RuntimeException {

    public TemaIdMalformadoException() {
        super("El id de tema debe ser un UUID válido.");
    }
}