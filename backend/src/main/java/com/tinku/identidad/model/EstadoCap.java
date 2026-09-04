package com.tinku.identidad.model;

/**
 * Estados de un Certificado de Antecedentes Penales (US-6, FR-ID-021..025).
 * {@code EN_REVISION_LEGAL} es el estado BR-CAP-02 — nunca se auto-resuelve
 * (requiere decisión manual documentada del Admin). {@code VENCIDO} lo setea
 * el job de Quartz a los 12 meses de emisión (FR-ID-025).
 */
public enum EstadoCap {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    EN_REVISION_LEGAL,
    VENCIDO
}
