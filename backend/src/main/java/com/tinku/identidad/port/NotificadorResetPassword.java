package com.tinku.identidad.port;

import com.tinku.identidad.model.Usuario;

/**
 * Puerto hacia el canal que le avisa al usuario su link de recuperación de
 * contraseña. Implementación: {@code NotificadorResetPasswordEmail} (Resend,
 * ADR-000-06, FASE2-03).
 */
@FunctionalInterface
public interface NotificadorResetPassword {

    /** {@code tokenPlano} es el único momento en que el valor sin hashear
     * existe fuera de la memoria del cliente que lo solicitó. */
    void notificar(Usuario usuario, String tokenPlano);
}
