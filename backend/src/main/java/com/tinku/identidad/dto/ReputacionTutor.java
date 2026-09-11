package com.tinku.identidad.dto;

import java.math.BigDecimal;

/** Reputación pública de un Tutor (M7, FR-REP-007). {@code calificacionPromedio}
 * viene {@code null} cuando {@code cantidadCalificaciones < 5} — nunca se expone
 * un promedio estadísticamente insignificante. */
public record ReputacionTutor(BigDecimal calificacionPromedio, long cantidadCalificaciones) {
}