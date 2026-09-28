package com.tinku.shared;

import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.identidad.service.UsuarioAutenticado;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resuelve el {@link Usuario} autenticado desde el principal del {@link
 * Authentication}: el que dejó el filtro JWT ({@link UsuarioAutenticado}) o, si el principal es
 * otro (tests con un token armado a mano), buscándolo por id. Los controllers lo usan en lugar de repetir el lookup
 * {@code findByDni + orElseThrow} (estado inconsistente → IllegalStateException
 * porque el usuario del JWT no existe en la BD).
 */
@Component
public class UsuarioActual {

    private final UsuarioRepository usuarioRepository;

    public UsuarioActual(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario obtener(Authentication authentication) {
        // AUD-036.1: el filtro JWT ya lo cargó; no se consulta dos veces.
        if (authentication.getPrincipal() instanceof UsuarioAutenticado principal) {
            return principal.getUsuario();
        }
        // AUD-027: el nombre del principal es el UUID del usuario.
        return usuarioRepository.findById(UUID.fromString(authentication.getName()))
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));
    }
}