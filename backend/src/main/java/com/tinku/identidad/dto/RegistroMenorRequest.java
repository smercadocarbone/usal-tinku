package com.tinku.identidad.dto;

import com.tinku.identidad.validacion.PasswordSegura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Alta de un perfil de MENOR a cargo, hecha por el Adulto Responsable
 * autenticado (FR-ID-020: el menor NUNCA se autorregistra). La foto del DNI
 * del menor viaja aparte (multipart), como en el alta de adulto.
 *
 * Incluye el consentimiento explícito y separado del T&C general
 * (BR-CONSENT-01), obligatorio en la misma transacción.
 */
public record RegistroMenorRequest(
        @NotBlank String dniDeclarado,
        @NotBlank String nombreDeclarado,
        @NotBlank String apellidoDeclarado,
        @NotNull LocalDate fechaNacimientoDeclarada,
        @NotBlank @PasswordSegura String password,
        @NotNull Boolean consentimientoExplicito,
        String versionTextoConsentimiento
) {
}
