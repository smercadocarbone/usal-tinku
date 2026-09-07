package com.tinku.matching;

import java.time.Instant;
import java.util.UUID;

/** Búsqueda guardada devuelta al frontend (guardar/listar). */
public record GuardadaResponse(UUID id, String textoBusqueda, Instant createdAt) {
}