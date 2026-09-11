package com.tinku.admin.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Cuerpo de POST /api/admin/financiero/transacciones/{id}/reembolso-parcial
 * (FR-PAG-010): devuelve una PARTE del escrow en el contexto de una disputa
 * gestionada manualmente por Soporte Financiero. El monto lo decide el Admin;
 * la diferencia de comisión de gateway la absorbe Tinku.
 */
public record ReembolsoParcialRequest(
        @NotNull @DecimalMin("0.01") BigDecimal monto) {
}