package com.tinku.seguridad.model;

/**
 * De dónde nace una {@link Sancion}. Cada origen dispara un track distinto:
 * una Denuncia pasa por descargo/SLA (Plan_M9 §2.3), una Alerta de kill-switch
 * no espera descargo (FR-SEC-004). Fuerza exactamente una referencia por fila
 * (CHECK de V13).
 */
public enum OrigenSancion {
    DENUNCIA,
    ALERTA_SEGURIDAD
}