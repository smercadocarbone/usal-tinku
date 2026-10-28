package com.tinku.aula.port;

import java.util.UUID;

/**
 * Puerto hacia M2 (Matching): si la materia de la clase muestra el cuerpo humano como parte del
 * contenido (Medicina, Anatomía), para el modo salud del kill-switch (ADR-M3-07 §4bis).
 * Implementación real: {@code com.tinku.matching.port.MateriasClaseReal}.
 */
public interface ContenidoAnatomicoProveedor {

    boolean esContenidoAnatomico(UUID trayectoId);
}
