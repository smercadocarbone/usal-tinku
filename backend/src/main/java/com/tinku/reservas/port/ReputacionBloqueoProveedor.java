package com.tinku.reservas.port;

import java.util.Set;
import java.util.UUID;

/**
 * FR-REP-006 (T-M4-10) — el Tutor con una calificación pendiente no puede
 * tener reservas nuevas. STUB: M7 (aún no existe) calculará este conjunto con
 * sus datos de reputación; el Chunk M7-C reemplaza esta implementación por la
 * real. La consulta se hace al momento de crear la Reserva (del lado de M4) y
 * la lista la posee M7 — M4 nunca la cachea.
 */
public interface ReputacionBloqueoProveedor {

    /** Tutores que tienen al menos una calificación de Estudiante pendiente. */
    Set<UUID> tutoresConCalificacionPendiente();
}