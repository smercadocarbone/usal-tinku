package com.tinku.seguridad;

import java.util.UUID;

/** La Alerta ya fue resuelta; no se resuelve dos veces (422). */
public class AlertaYaResueltaException extends RuntimeException {
    public AlertaYaResueltaException(UUID id) {
        super("La alerta de seguridad ya está resuelta: " + id);
    }
}