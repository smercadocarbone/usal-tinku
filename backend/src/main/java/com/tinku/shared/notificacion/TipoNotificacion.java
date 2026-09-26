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
    DENUNCIA_RECIBIDA(true),

    /** PT10 (T02): el Tutor perdió la habilitación para menores (CAP vencido) y se canceló
     *  la clase con reembolso total. Al Adulto Responsable. Sin datos del CAP (T03 §2.3).
     *  Datos: reservaId, horario. */
    CLASE_CANCELADA_TUTOR_SIN_HABILITACION(true),

    /** ADR-M5-02: no se pudo renovar la conexión de MercadoPago del Tutor; deja de ser
     *  reservable hasta reconectar. Al Tutor. Sin datos. */
    MP_CUENTA_DESCONECTADA(true),

    // ---- Ciclo de la clase (2026-09-26). Artículo II: nunca un dato del menor en los datos.

    /** Al Tutor, cuando se confirma (se paga) una clase suya. Datos: reservaId, horario, duracion. */
    CLASE_RESERVADA(true),

    /** A la otra parte cuando se cancela una clase confirmada. Datos: reservaId, horario, canceladaPor
     *  ("tutor" | "alumno"). */
    CLASE_CANCELADA(true),

    /** Al Tutor, cuando quien pagó le cambia el horario. Datos: reservaId, horarioAnterior, horario. */
    CLASE_REPROGRAMADA(true),

    /** 24 hs antes (Tabla_Tiempos), a los participantes. No se manda si se reservó con menos margen.
     *  Datos: reservaId, horario. */
    RECORDATORIO_CLASE(true),

    /** Al abrir la sala (T-5, Tabla_Tiempos), a quienes dan y toman la clase. Datos: sesionId, horario. */
    CLASE_POR_EMPEZAR(true),

    /** Al horario de inicio, a quien todavía no entró. Datos: sesionId, horario. */
    CLASE_EMPEZO(true),

    /** Al Tutor, cuando se le libera el pago de una clase (FR-PAG-002). Datos: reservaId. */
    PAGO_LIBERADO(true),

    /** Al Tutor, cuando Moderación revisa su credencial académica. Datos: resultado ("aprobada" | "rechazada"). */
    CREDENCIAL_REVISADA(true),

    /** Al Tutor, cuando Moderación revisa su CAP. Datos: resultado ("aprobado" | "rechazado" | "en_revision").
     *  Nunca la categoría del antecedente. */
    CAP_REVISADO(true);

    private final boolean porEmail;

    TipoNotificacion(boolean porEmail) {
        this.porEmail = porEmail;
    }

    public boolean porEmail() {
        return porEmail;
    }
}
