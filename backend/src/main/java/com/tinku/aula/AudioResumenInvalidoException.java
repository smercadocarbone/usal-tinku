package com.tinku.aula;

/** ADR-M3-04: el audio del resumen no se acepta (condiciones del Art. V, formato o tamaño) → 422. */
public class AudioResumenInvalidoException extends RuntimeException {
    public AudioResumenInvalidoException(String mensaje) {
        super(mensaje);
    }
}
