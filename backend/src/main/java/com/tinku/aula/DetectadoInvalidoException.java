package com.tinku.aula;

import java.util.UUID;

/** {@code detectadoId} del kill-switch no es participante de la Sesión (422). */
public class DetectadoInvalidoException extends RuntimeException {
    public DetectadoInvalidoException(UUID detectadoId) {
        super("El usuario detectado no es participante de la sesión.");
    }
}