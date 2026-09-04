package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo del PATCH de revisión de CAP (T-M1-16). {@code categoriaAntecedente}
 * es texto libre que carga el Admin para trazabilidad (qué categoría de
 * BR-CAP-01/02 aplicó), no para automatizar la decisión.
 */
public record RevisarCapRequest(
        @NotNull AccionRevisionCap accion,
        String categoriaAntecedente
) {
}
