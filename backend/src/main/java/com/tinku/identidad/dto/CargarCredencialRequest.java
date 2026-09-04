package com.tinku.identidad.dto;

import com.tinku.identidad.model.TipoCredencial;
import jakarta.validation.constraints.NotNull;

/**
 * Carga de Credencial Académica de un Tutor (US-4, FR-ID-008/012).
 * {@code tipoDocumento} es un enum con lista cerrada (BR-ID-01); el archivo
 * viaja aparte (multipart). Solo un Tutor puede cargar.
 */
public record CargarCredencialRequest(
        @NotNull TipoCredencial tipoDocumento
) {
}
