package com.tinku.seguridad;

/**
 * AUD-011: nadie se denuncia a sí mismo. Se traduce a 422.
 */
public class AutoDenunciaException extends RuntimeException {
    public AutoDenunciaException() {
        super("No se puede presentar una denuncia contra uno mismo.");
    }
}
