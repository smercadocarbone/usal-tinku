package com.tinku.identidad.dto;

/**
 * Acción de revisión del CAP por el panel Admin (T-M1-16):
 *  - {@code APROBAR}: sin antecedentes → aprobado (habilita matching).
 *  - {@code RECHAZAR}: antecedente de la lista BR-CAP-01 → rechazo sin
 *    excepción ni apelación en producto (FR-ID-023).
 *  - {@code EN_REVISION_LEGAL}: antecedente fuera de BR-CAP-01 o proceso en
 *    trámite → decisión manual documentada, nunca auto-resuelto (BR-CAP-02,
 *    FR-ID-024).
 */
public enum AccionRevisionCap {
    APROBAR,
    RECHAZAR,
    EN_REVISION_LEGAL
}
