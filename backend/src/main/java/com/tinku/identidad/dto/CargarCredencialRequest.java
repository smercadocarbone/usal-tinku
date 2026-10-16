package com.tinku.identidad.dto;

import com.tinku.identidad.model.TipoCredencial;
import jakarta.validation.constraints.NotNull;

/**
 * Carga de Credencial Académica de un Tutor (US-4, FR-ID-008/012). El archivo viaja aparte
 * (multipart). Solo un Tutor puede cargar.
 *
 * <p>Con {@code tinku.especialidades.habilitado} apagado solo cuenta {@code tipoDocumento} (lista
 * cerrada de BR-ID-01) y el resto se ignora. Encendido (ADR-M1-06), se exigen los datos
 * estructurados y el consentimiento para consultar registros oficiales; los valida
 * {@code CargaCredencialService}.</p>
 */
public record CargarCredencialRequest(
        @NotNull TipoCredencial tipoDocumento,
        String institucion,
        String denominacion,
        Integer anioEgreso,
        Boolean esDigital,
        String enlaceVerificacion,
        Boolean aceptaConsultaRegistros
) {
    public CargarCredencialRequest(TipoCredencial tipoDocumento) {
        this(tipoDocumento, null, null, null, null, null, null);
    }
}
