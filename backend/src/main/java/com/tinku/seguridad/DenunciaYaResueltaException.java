package com.tinku.seguridad;

import java.util.UUID;

/** La Denuncia ya tiene una resolución; no se resuelve dos veces (422). */
public class DenunciaYaResueltaException extends RuntimeException {
    public DenunciaYaResueltaException(UUID id) {
        super("La denuncia ya está resuelta: " + id);
    }
}