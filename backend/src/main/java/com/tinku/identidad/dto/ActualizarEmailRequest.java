package com.tinku.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Cambio de email del usuario autenticado ("editar cuenta"). */
public record ActualizarEmailRequest(
        @NotBlank @Email(regexp = com.tinku.identidad.dto.FormatoEmail.REGEX, message = "debe ser un email válido, por ejemplo nombre@gmail.com") String email
) {
}
