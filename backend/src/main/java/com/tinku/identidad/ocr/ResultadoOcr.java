package com.tinku.identidad.ocr;

import java.time.LocalDate;

/**
 * Resultado de procesar un documento de identidad.
 * Ver Plan_M1_Identidad_Perfiles.md, sección 2.1, paso 3.
 */
public record ResultadoOcr(
        boolean documentoLegible,
        String dniExtraido,
        String nombreExtraido,
        String apellidoExtraido,
        LocalDate fechaNacimientoExtraida
) {
    public static ResultadoOcr ilegible() {
        return new ResultadoOcr(false, null, null, null, null);
    }
}
