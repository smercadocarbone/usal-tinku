package com.tinku.identidad.dto;

import com.tinku.identidad.validacion.PasswordSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
        @NotBlank @PasswordSegura String password
) {
}
