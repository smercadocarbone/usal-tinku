package com.tinku.shared.notificacion;

/**
 * Tipos de aviso a usuarios (FASE2-03, AUD-014). {@code porEmail}: además de la
 * bandeja in-app, sale por email (ADR-000-06) — solo lo que el usuario tiene que
 * saber aunque no entre a la app.
 */
public enum TipoNotificacion {

    /** Spec_M3 US-6 / D2-bis: al Adulto Responsable, inmediato e incondicional. Sin
     *  nombre del Tutor, sin clip, sin describir lo detectado. Datos: sesionId, fecha. */
    KILLSWITCH_MENOR(true),

    /** FR-SEC-010: al denunciado, para que sepa que corre su plazo de descargo. Sin
     *  nada del denunciante (FR-SEC-006). Datos: denunciaId, descargoVenceAt. */
    DENUNCIA_RECIBIDA(true);

    private final boolean porEmail;

    TipoNotificacion(boolean porEmail) {
        this.porEmail = porEmail;
    }

    public boolean porEmail() {
        return porEmail;
    }
}
