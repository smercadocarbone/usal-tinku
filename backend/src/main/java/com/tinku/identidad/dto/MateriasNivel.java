package com.tinku.identidad.dto;

import java.util.List;

/** Materias/nivel de un Tutor, leídos del catálogo de M2
 * ({@code matching.materias_niveles} vía {@code matching.perfiles_tutor_matching}). */
public record MateriasNivel(List<String> materias, String nivel) {
}