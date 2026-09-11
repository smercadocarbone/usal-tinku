package com.tinku.seguridad;

/**
 * FR-SEC-001 (US-1): el menor tiene cuenta y sesión propias pero no puede
 * presentar Denuncias — restricción a nivel de autorización del endpoint
 * (403), no una limitación de la UI. Su Adulto Responsable la presenta en su
 * nombre (Constitución, Artículo II).
 */
public class MenorNoDenunciaException extends RuntimeException {
    public MenorNoDenunciaException() {
        super("El perfil de menor no presenta denuncias; lo hace su Adulto Responsable.");
    }
}