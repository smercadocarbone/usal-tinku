package com.tinku.aula;

/**
 * Evidencia del kill-switch inválida (422): URL del clip vacía o no es una URL
 * http(s), o el clip excede los 30s del buffer (BR-KS-01, T-M3-08).
 */
public class EvidenciaInvalidaException extends RuntimeException {
    public EvidenciaInvalidaException(String motivo) {
        super("Evidencia inválida: " + motivo);
    }
}