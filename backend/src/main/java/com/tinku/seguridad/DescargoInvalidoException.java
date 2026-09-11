package com.tinku.seguridad;

/** Descargo inválido — máximo 300 caracteres en Denuncia (FR-SEC-006) y Alerta (US-2). */
public class DescargoInvalidoException extends RuntimeException {
    public DescargoInvalidoException() {
        super("El descargo no puede superar los 300 caracteres.");
    }
}