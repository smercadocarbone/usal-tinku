package com.tinku.identidad.dto;

import com.tinku.identidad.model.NivelEspecialidad;

import java.util.List;

/** PUT /api/tutores/me/especialidades (ADR-M1-06): reemplaza la lista completa (1 a 3). */
public record GuardarEspecialidadesRequest(List<Especialidad> especialidades) {

    public record Especialidad(String materia, NivelEspecialidad nivelMaximo) {
    }
}
