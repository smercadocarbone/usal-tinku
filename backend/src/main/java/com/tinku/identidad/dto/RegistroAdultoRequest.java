package com.tinku.identidad.dto;

import com.tinku.identidad.validacion.PasswordSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
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
        boolean capacidadAdultoResponsable,
        /** FR-ID-031 / ADR-M3-05: sin aceptar los Términos no se crea la cuenta (400). */
        @NotNull @AssertTrue(message = "Tenés que aceptar los Términos y Condiciones.") Boolean aceptaTerminos
) {
    /** Clientes y tests anteriores al campo: el wizard siempre lo manda. */
    public RegistroAdultoRequest(String dniDeclarado, String nombreDeclarado, String apellidoDeclarado,
                                 LocalDate fechaNacimientoDeclarada, String email, String password,
                                 boolean capacidadEstudiante, boolean capacidadAdultoResponsable) {
        this(dniDeclarado, nombreDeclarado, apellidoDeclarado, fechaNacimientoDeclarada, email, password,
                capacidadEstudiante, capacidadAdultoResponsable, true);
    }
}
