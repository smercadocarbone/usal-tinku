package com.tinku.identidad.dto;

import com.tinku.identidad.validacion.PasswordSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Alta de Tutor (FR-ID-007): mismo flujo de OCR que el alta de adulto
 * (edad ≥ 18, coincidencia nombre/apellido/DNI, unicidad de DNI), sin
 * excepciones para menores. La foto del DNI viaja aparte (multipart).
 */
public record RegistroTutorRequest(
        @NotBlank String dniDeclarado,
        @NotBlank String nombreDeclarado,
        @NotBlank String apellidoDeclarado,
        @NotNull LocalDate fechaNacimientoDeclarada,
        @NotBlank(message = "es obligatorio") @Email(message = "debe ser un email válido") String email,
        @NotBlank @PasswordSegura String password,
        /** FR-ID-031 / ADR-M3-05: sin aceptar los Términos no se crea la cuenta (400). */
        @NotNull @AssertTrue(message = "Tenés que aceptar los Términos y Condiciones.") Boolean aceptaTerminos
) {
    /** Clientes y tests anteriores al campo: el wizard siempre lo manda. */
    public RegistroTutorRequest(String dniDeclarado, String nombreDeclarado, String apellidoDeclarado,
                                LocalDate fechaNacimientoDeclarada, String email, String password) {
        this(dniDeclarado, nombreDeclarado, apellidoDeclarado, fechaNacimientoDeclarada, email, password, true);
    }
}
