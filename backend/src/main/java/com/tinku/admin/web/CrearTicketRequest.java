package com.tinku.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creación de ticket desde el canal "contactar a soporte" (US-7). El
 * {@code origenModulo} (ej. {@code M1.credencial_agotada}, {@code M5.pago_fallido})
 * decide solo el enrutamiento; de quién es el ticket lo decide el token, no el
 * cliente (no se acepta un {@code usuarioId} en el body).
 */
public record CrearTicketRequest(
        @NotBlank @Size(max = 60) String origenModulo,
        @NotBlank @Size(max = 200) String asunto,
        @NotBlank @Size(max = 1000) String detalle) {
}