package com.tinku.identidad.port;

import com.tinku.identidad.model.Usuario;

/**
 * Puerto hacia el canal que le avisa al usuario su link de recuperación de
 * contraseña. Elegir el proveedor real (email transaccional, SMS) requiere
 * su propio ADR (AGENTS.md §2, mismo criterio que ADR-M1-01 para OCR) — no
 * es una decisión de este cambio. Implementación actual:
 * {@code NotificadorResetPasswordLog} (deja el link en el log de la app).
 */
@FunctionalInterface
public interface NotificadorResetPassword {

    /** {@code tokenPlano} es el único momento en que el valor sin hashear
     * existe fuera de la memoria del cliente que lo solicitó. */
    void notificar(Usuario usuario, String tokenPlano);
}
