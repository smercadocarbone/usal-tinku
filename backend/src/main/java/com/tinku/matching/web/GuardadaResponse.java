package com.tinku.matching.web;

import java.time.Instant;
import java.util.UUID;

/** Búsqueda guardada devuelta al frontend (guardar/listar). */
public record GuardadaResponse(UUID id, String textoBusqueda, Instant createdAt,
                               /** R-E E-2: los filtros con que se guardó (nombres del API). */
                               java.util.Map<String, Object> filtros) {

    public static GuardadaResponse from(com.tinku.matching.model.BusquedaGuardada g) {
        return new GuardadaResponse(g.getId(), g.getTextoBusqueda(), g.getCreatedAt(),
                g.getFiltros() == null ? java.util.Map.of() : g.getFiltros());
    }
}