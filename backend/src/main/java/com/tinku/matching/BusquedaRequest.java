package com.tinku.matching;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Body de POST /api/busquedas (contrato 2b): texto libre en lenguaje natural y/o
 * filtros de catálogo {@code nombre} y {@code filtro_materia}. Al menos uno de
 * los tres debe venir no vacío (422 si no) — se valida en el controller, no con
 * @NotBlank en cada campo. Los nombres de la API son snake_case
 * (texto_busqueda / filtro_materia): {@code @JsonProperty} los mapea a los
 * campos del record. El texto libre queda acotado a la columna
 * {@code texto_busqueda} (V7).
 */
public record BusquedaRequest(
        @JsonProperty("texto_busqueda")
        @Size(max = 500, message = "El texto de búsqueda no puede superar los 500 caracteres.")
        String textoBusqueda,
        @Size(max = 200, message = "El nombre del tema no puede superar los 200 caracteres.")
        String nombre,
        @JsonProperty("filtro_materia")
        @Size(max = 100, message = "La materia no puede superar los 100 caracteres.")
        String filtroMateria
) {
    /**
     * Constructor de conveniencia de un solo campo: los tests/guardadas que
     * usan solo texto libre siguen funcionando igual que antes de M2-F.
     */
    public BusquedaRequest(String textoBusqueda) {
        this(textoBusqueda, null, null);
    }
}