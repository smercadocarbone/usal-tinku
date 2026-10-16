package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Datos mínimos para la verificación previa del DNI (OCR) ANTES de pedir
 * email/contraseña en el wizard de registro. Sin password: la cuenta todavía
 * no existe. Ver UsuarioController/verificar-dni y UsuarioService.
 */
public record VerificarDniRequest(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = FormatoDni.REGEX, message = FormatoDni.MENSAJE) String dniDeclarado,
        @NotBlank String nombreDeclarado,
        @NotBlank String apellidoDeclarado,
        @NotNull LocalDate fechaNacimientoDeclarada
) {
}