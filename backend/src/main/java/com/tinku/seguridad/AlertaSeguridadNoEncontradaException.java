package com.tinku.seguridad;

import java.util.UUID;

/** Alerta de Seguridad inexistente (404). */
public class AlertaSeguridadNoEncontradaException extends RuntimeException {
    public AlertaSeguridadNoEncontradaException(UUID id) {
        super("Alerta de seguridad no encontrada: " + id);
    }
}