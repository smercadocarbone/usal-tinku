package com.tinku.matching.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Un resultado de POST /api/busquedas (camelCase, convención del API pública).
 * {@code noAutorizado} se setea TRUE en búsquedas de un menor cuando el Tutor
 * no está en su lista de autorización — el frontend muestra "Solicitar
 * autorización" (FR-MATCH-005).
 * {@code porArea}/{@code area} (FR-MATCH-011): no hubo tutor para lo que se escribió y el
 * resultado es una recomendación de tutores del área más cercana del catálogo
 * (p. ej. "Matemática · Secundario"); el frontend lo aclara en vez de mostrarlo como match.
 * {@code precioHora}/{@code proximoHorario} (FR-MATCH-013): tarifa vigente y primer bloque libre
 * de los próximos 14 días (null si no configuró tarifa o no tiene horarios en ese plazo). */
public record BusquedaResponse(UUID tutorId, double score, boolean noAutorizado, boolean porArea, String area,
                               BigDecimal precioHora, Instant proximoHorario) {

    public BusquedaResponse(UUID tutorId, double score, boolean noAutorizado, boolean porArea, String area) {
        this(tutorId, score, noAutorizado, porArea, area, null, null);
    }

    public BusquedaResponse(UUID tutorId, double score, boolean noAutorizado) {
        this(tutorId, score, noAutorizado, false, null);
    }

    public BusquedaResponse conDatosDeReserva(BigDecimal precioHora, Instant proximoHorario) {
        return new BusquedaResponse(tutorId, score, noAutorizado, porArea, area, precioHora, proximoHorario);
    }
}
