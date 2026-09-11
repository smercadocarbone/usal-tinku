package com.tinku.matching;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body de POST /api/busquedas y de POST /api/busquedas/guardadas: texto libre
 * en lenguaje natural, acotado a la columna {@code texto_busqueda} (V7).
 *
 * Los filtros de materia/nivel/disponibilidad de FR-MATCH-001 quedan por fuera
 * por ahora: el texto libre sigue siendo el insumo de la búsqueda semántica. */
public record BusquedaRequest(
        @NotBlank(message = "El texto de búsqueda no puede estar vacío.")
        @Size(max = 500, message = "El texto de búsqueda no puede superar los 500 caracteres.")
        String textoBusqueda
) {
}