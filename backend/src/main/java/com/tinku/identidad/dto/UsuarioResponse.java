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
        boolean capacidadAdultoResponsable
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(), u.getNombre(), u.getApellido(), u.getTipo(),
                u.isCapacidadEstudiante(), u.isCapacidadAdultoResponsable()
        );
    }
}
