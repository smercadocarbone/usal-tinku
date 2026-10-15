package com.tinku.identidad.port;

/** El validador de firmas no respondió o respondió algo que no se puede leer (ADR-M1-10). */
public class ValidadorNoDisponibleException extends RuntimeException {
    public ValidadorNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ValidadorNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
