package com.tinku.pagos.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Estados de una {@link Transaccion} (Plan M5 §1, FR-PAG-001/002/003/007). Los
 * valores de base de datos son minúsculas (V11__m5_pagos.sql, CHECK de la columna
 * {@code estado}: {'retenido_escrow','liberado','reembolsado','pausado_denuncia'}).
 *
 * Transiciones actuales (Chunk M5-B, {@code EscrowService}):
 * <pre>
 * retenido_escrow ─┬─ sesion.finalizada        → (liberar_at = fin + 24h, sigue retenido)
 *                  ├─ no_show_estudiante       → liberado   (liberación inmediata, Plan §2)
 *                  ├─ interrumpida / no_show_* / killswitch_* → reembolsado
 *                  └─ denuncia.registrada      → pausado_denuncia
 * </pre>
 * La liberación efectiva al Tutor (retenido_escrow/liberar_at → liberado) la
 * ejecuta el job de Chunk M5-C. {@code denuncia.resuelta} (M5 side) queda
 * pendiente: payload y resolución dependen de M9-D + M5-C/M5-D (documentado).
 */
public enum EstadoTransaccion {
    RETENIDO_ESCROW("retenido_escrow"),
    LIBERADO("liberado"),
    REEMBOLSADO("reembolsado"),
    PAUSADO_DENUNCIA("pausado_denuncia");

    private final String valor;

    EstadoTransaccion(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static EstadoTransaccion parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de transacción inválido: " + valor));
    }
}