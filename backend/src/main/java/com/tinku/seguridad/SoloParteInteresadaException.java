package com.tinku.seguridad;

/**
 * 403: la operación solo la puede la parte interesada — el denunciado descarga
 * su propia Denuncia (FR-SEC-006) y el Tutor detectado descarga su propia
 * Alerta (US-2). Un tercero no interviene.
 */
public class SoloParteInteresadaException extends RuntimeException {
    public SoloParteInteresadaException() {
        super("Solo la parte interesada puede realizar esta acción.");
    }
}