package com.tinku.matching;

import java.util.UUID;

/** Un resultado de POST /api/busquedas (camelCase, convención del API pública).
 * {@code noAutorizado} se setea TRUE en búsquedas de un menor cuando el Tutor
 * no está en su lista de autorización — el frontend muestra "Solicitar
 * autorización" (FR-MATCH-005).
 * {@code porArea}/{@code area} (FR-MATCH-011): no hubo tutor para lo que se escribió y el
 * resultado es una recomendación de tutores del área más cercana del catálogo
 * (p. ej. "Matemática · Secundario"); el frontend lo aclara en vez de mostrarlo como match. */
public record BusquedaResponse(UUID tutorId, double score, boolean noAutorizado, boolean porArea, String area) {

    public BusquedaResponse(UUID tutorId, double score, boolean noAutorizado) {
        this(tutorId, score, noAutorizado, false, null);
    }
}
