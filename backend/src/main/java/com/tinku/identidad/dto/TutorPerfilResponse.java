package com.tinku.identidad.dto;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// FIXME AUD-003 (auditoría 2026-09-21): este DTO no expone el DNI, correcto. Pero el DNI SÍ
// sale del sistema por otra vía: SesionService.obtenerToken() lo usa como identity de LiveKit.
/**
 * Perfil PÚBLICO de un Tutor (GET /api/tutores/{id}). Base = campos de
 * {@link UsuarioResponse} (nunca exportera passwordHash ni DNI) + materias y
 * nivel del catálogo de M2 + reputación pública de M7 (promedio null si count
 * < 5, FR-REP-007). Sin materias configuradas o sin calificaciones suficientes,
 * listas vacías y promedio null.
 */
public record TutorPerfilResponse(
        UUID id,
        String nombre,
        String apellido,
        TipoUsuario tipo,
        boolean capacidadEstudiante,
        boolean capacidadAdultoResponsable,
        List<String> materias,
        String nivel,
        BigDecimal calificacionPromedio,
        long cantidadCalificaciones
) {
    public static TutorPerfilResponse of(Usuario tutor, MateriasNivel materiasNivel, ReputacionTutor reputacion) {
        return new TutorPerfilResponse(
                tutor.getId(),
                tutor.getNombre(),
                tutor.getApellido(),
                tutor.getTipo(),
                tutor.isCapacidadEstudiante(),
                tutor.isCapacidadAdultoResponsable(),
                materiasNivel == null ? List.of() : materiasNivel.materias(),
                materiasNivel == null ? null : materiasNivel.nivel(),
                reputacion.calificacionPromedio(),
                reputacion.cantidadCalificaciones()
        );
    }
}