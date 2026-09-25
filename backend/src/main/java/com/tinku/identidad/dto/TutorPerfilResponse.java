package com.tinku.identidad.dto;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Perfil PÚBLICO de un Tutor (GET /api/tutores/{id}). Nunca exporta
 * passwordHash, DNI ni email. Materias y nivel del catálogo de M2 + reputación
 * pública de M7 (promedio null si count < 5, FR-REP-007).
 *
 * <p>UX-04 §2 / U1: suma {@code bio}, {@code tieneFoto} (los bytes se piden a
 * {@code GET /api/tutores/{id}/foto}), {@code verificado} (tiene al menos una
 * Credencial Académica aprobada) y {@code precioHora} (la tarifa que el Tutor
 * configuró; {@code null} = todavía no la definió). Se sacaron
 * {@code capacidadEstudiante}/{@code capacidadAdultoResponsable}: no le sirven a
 * quien mira un perfil público.</p>
 */
public record TutorPerfilResponse(
        UUID id,
        String nombre,
        String apellido,
        TipoUsuario tipo,
        List<String> materias,
        String nivel,
        BigDecimal calificacionPromedio,
        long cantidadCalificaciones,
        String bio,
        boolean tieneFoto,
        boolean verificado,
        BigDecimal precioHora,
        /** FR-ID-026 / T03 §2.2: CAP aprobado y vigente. Nada más del CAP se expone a terceros. */
        boolean habilitadoParaMenores
) {
    public static TutorPerfilResponse of(Usuario tutor, MateriasNivel materiasNivel, ReputacionTutor reputacion,
                                         boolean verificado, BigDecimal precioHora,
                                         boolean habilitadoParaMenores) {
        return new TutorPerfilResponse(
                tutor.getId(),
                tutor.getNombre(),
                tutor.getApellido(),
                tutor.getTipo(),
                materiasNivel == null ? List.of() : materiasNivel.materias(),
                materiasNivel == null ? null : materiasNivel.nivel(),
                reputacion.calificacionPromedio(),
                reputacion.cantidadCalificaciones(),
                tutor.getBio(),
                tutor.getFotoRef() != null,
                verificado,
                precioHora,
                habilitadoParaMenores
        );
    }
}
