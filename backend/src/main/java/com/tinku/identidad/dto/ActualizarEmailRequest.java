package com.tinku.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Cambio de email del usuario autenticado ("editar cuenta"). */
public record ActualizarEmailRequest(
        @NotBlank @Email String email
) {
}
