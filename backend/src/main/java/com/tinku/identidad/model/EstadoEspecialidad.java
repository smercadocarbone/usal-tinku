package com.tinku.identidad.model;

/**
 * Estado de una especialidad del Tutor (ADR-M1-06). No se guarda: sale de las credenciales
 * APROBADAS que la cubren — la de mayor peso gana.
 */
public enum EstadoEspecialidad {
    /** Ninguna credencial aprobada la cubre. */
    DECLARADA,
    /** La cubre una credencial aprobada "solo documento". */
    RESPALDO_DOCUMENTAL,
    /** La cubre una credencial aprobada contra el registro oficial. */
    VERIFICADA
}
