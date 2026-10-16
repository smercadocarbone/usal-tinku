package com.tinku.identidad.dto;

import com.tinku.identidad.model.EstadoEspecialidad;
import com.tinku.identidad.model.NivelEspecialidad;

import java.util.UUID;

/** Una especialidad del Tutor con su estado (ADR-M1-06): insignia por especialidad. */
public record EspecialidadResponse(UUID id, String materia, NivelEspecialidad nivelMaximo, EstadoEspecialidad estado) {
}
