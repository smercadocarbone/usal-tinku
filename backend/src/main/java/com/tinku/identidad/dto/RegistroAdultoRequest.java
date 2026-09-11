package com.tinku.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request de alta de Usuario adulto. La foto del DNI viaja aparte
 * (multipart), no en este JSON — ver UsuarioController.
 */
public record RegistroAdultoRequest(
        @NotBlank String dniDeclarado,
        @NotBlank String nombreDeclarado,
        @NotBlank String apellidoDeclarado,
        @NotNull LocalDate fechaNacimientoDeclarada,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        boolean capacidadEstudiante,
        boolean capacidadAdultoResponsable
) {
}
