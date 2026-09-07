package com.tinku.identidad.model;

/**
 * Estados de una Credencial Académica (US-4, FR-ID-008) — tabla
 * {@code credenciales_academicas} V2. Solo el panel de Admin (M8) transiciona
 * desde {@code PENDIENTE}.
 */
public enum EstadoCredencial {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
