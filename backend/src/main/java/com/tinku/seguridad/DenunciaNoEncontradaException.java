package com.tinku.seguridad;

import java.util.UUID;

/** Denuncia inexistente o de otro módulo (404). */
public class DenunciaNoEncontradaException extends RuntimeException {
    public DenunciaNoEncontradaException(UUID id) {
        super("Denuncia no encontrada: " + id);
    }
}