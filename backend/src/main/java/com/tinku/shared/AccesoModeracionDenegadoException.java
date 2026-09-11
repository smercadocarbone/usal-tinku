package com.tinku.shared;

/**
 * 403 de Admin de Moderacion y Seguridad (M7/M9). Cada modulo mapea esta
 * excepcion en su propio ExceptionHandler ({@code RestControllerAdvice} scoped
 * a su paquete), mismo patron que {@code SoloParticipanteException}.
 */
public class AccesoModeracionDenegadoException extends RuntimeException {

    public AccesoModeracionDenegadoException() {
        super("No autorizado: esta acción requiere rol de Moderación y Seguridad.");
    }
}