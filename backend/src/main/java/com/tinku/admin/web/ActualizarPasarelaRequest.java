package com.tinku.admin.web;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PATCH /api/admin/financiero/pasarela}: define si la pasarela
 * cobra real (true) o entra en modo Bypass (false). {@code @NotNull}: el valor
 * debe venir explícito — un PATCH sin body es un error, no un "default".
 */
public record ActualizarPasarelaRequest(@NotNull Boolean habilitada) {
}