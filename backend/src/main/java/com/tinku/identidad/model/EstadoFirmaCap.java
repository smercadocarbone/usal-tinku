package com.tinku.identidad.model;

/**
 * Estado de la verificación de la firma digital del CAP con el validador oficial (ADR-M1-10).
 * Solo {@code VALIDA} habilita la aprobación cuando el validador está encendido.
 */
public enum EstadoFirmaCap {
    /** Validador apagado, o el CAP ya no está en revisión. */
    NO_VERIFICADA,
    /** En la cola, esperando su turno. */
    PENDIENTE,
    /** Enviado al validador; falta el resultado. */
    EN_PROCESO,
    /** Firma válida del Registro Nacional de Reincidencia, sobre el mismo archivo. */
    VALIDA,
    /** Sin firma, firma inválida, PDF protegido o archivo distinto del enviado. */
    INVALIDA,
    /** Firma válida, pero de alguien que no es el Registro Nacional de Reincidencia. */
    FIRMANTE_NO_RECONOCIDO,
    /** El validador no respondió después de todos los reintentos. */
    ERROR
}
