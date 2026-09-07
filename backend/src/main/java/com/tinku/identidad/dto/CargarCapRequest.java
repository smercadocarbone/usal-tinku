package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Carga de un Certificado de Antecedentes Penales (CAP, US-6 / T-M1-15).
 * Fecha de emisión declarada/extraída del documento; el PDF viaja aparte
 * (multipart). Solo un Tutor puede cargar.
 */
public record CargarCapRequest(
        @NotNull LocalDate fechaEmision
) {
}
