package com.tinku.identidad.dto;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;

import java.util.UUID;

/** Nunca exponer `passwordHash` — este DTO es la única forma permitida
 * de serializar un Usuario hacia afuera del backend. */
public record UsuarioResponse(
        UUID id,
        String nombre,
        String apellido,
        TipoUsuario tipo,
        boolean capacidadEstudiante,
        boolean capacidadAdultoResponsable,
        String email,
        /** R-B B-10 (T1-7): rol de admin activo ("moderacion_seguridad", "soporte_financiero") o
         *  null. Solo lo completa GET /api/usuarios/me; así el front no pide /api/admin/yo en
         *  cada pantalla de quien no es admin. */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String rolAdmin
) {
    public static UsuarioResponse from(Usuario u) {
        return from(u, null);
    }

    public static UsuarioResponse from(Usuario u, String rolAdmin) {
        return new UsuarioResponse(
                u.getId(), u.getNombre(), u.getApellido(), u.getTipo(),
                u.isCapacidadEstudiante(), u.isCapacidadAdultoResponsable(), u.getEmail(), rolAdmin
        );
    }
}
