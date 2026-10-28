package com.tinku.reservas.port;

import java.util.UUID;

/**
 * Puerto hacia M2 (Matching): si la materia elegida al reservar es una de las que dicta el Tutor
 * (ADR-M3-07 §4bis). Implementación real: {@code com.tinku.matching.port.MateriasClaseReal}.
 */
public interface MateriasTutorProveedor {

    boolean dicta(UUID tutorId, UUID trayectoId);
}
