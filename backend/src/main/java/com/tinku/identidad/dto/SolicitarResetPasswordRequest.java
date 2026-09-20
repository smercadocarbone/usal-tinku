package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotBlank;

/** Pedido de recuperación de contraseña, por DNI (mismo identificador del
 * login — el usuario no necesariamente recuerda con qué email se registró). */
public record SolicitarResetPasswordRequest(
        @NotBlank String dni
) {
}
