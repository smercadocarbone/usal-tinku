package com.tinku.matching;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Body de PUT /api/perfil-matching: nivel + materias del catálogo (FR-MATCH-006). */
public record CargarPerfilMatchingRequest(
        @NotBlank(message = "El nivel no puede estar vacío.")
        String nivel,
        @NotEmpty(message = "Elegí al menos una materia.")
        List<@NotBlank(message = "La materia no puede estar vacía.") String> materias
) {
}