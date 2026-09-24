package com.tinku.identidad.dto;

import com.tinku.identidad.validacion.PasswordSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        @NotBlank @PasswordSegura String password,
        boolean capacidadEstudiante,
        boolean capacidadAdultoResponsable
) {
}
