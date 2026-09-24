package com.tinku.identidad.dto;

import com.tinku.identidad.model.EstadoCredencial;

/**
 * UX-06 §1 — qué le falta al Tutor para recibir alumnos. {@code visibleEnBusquedas}
 * es el flag real del matching ({@code activo_para_matching}, FR-ID-025): la regla
 * vive en el backend, la UI solo la muestra. Los horarios los lee la UI de
 * {@code GET /api/tutores/{id}/franjas} (M4).
 *
 * @param ultimaCredencial estado de la última credencial cargada; {@code null} si no cargó ninguna.
 * @param tieneCredencialAprobada tiene al menos una aprobada (aunque la última siga en revisión, B12).
 */
public record EstadoPerfilTutorResponse(
        boolean visibleEnBusquedas,
        EstadoCredencial ultimaCredencial,
        boolean tieneCredencialAprobada,
        boolean tieneMaterias,
        boolean tienePrecio,
        boolean tieneBio,
        boolean tieneFoto
) {
}
