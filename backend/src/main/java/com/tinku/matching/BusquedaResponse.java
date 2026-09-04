package com.tinku.matching;

import java.util.UUID;

/** Un resultado de POST /api/busquedas (camelCase, convención del API pública).
 * {@code noAutorizado} se setea TRUE en búsquedas de un menor cuando el Tutor
 * no está en su lista de autorización — el frontend muestra "Solicitar
 * autorización" (FR-MATCH-005). */
public record BusquedaResponse(UUID tutorId, double score, boolean noAutorizado) {
}