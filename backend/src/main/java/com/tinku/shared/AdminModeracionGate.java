package com.tinku.shared;

import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Gate de autorizacion para endpoints de Moderacion y Seguridad (los consume
 * M7 — calificaciones ocultas — y M9 — resolucion de Denuncias/Alertas).
 * Mientras la tabla {@code admins} de M8 no exista, la identidad de un Admin de
 * Moderacion es una lista permitida de DNIs de usuarios in
 * {@code tinku.admin.moderacion.ids} (fail-closed: sin configurar, toda
 * llamada → {@link AccesoModeracionDenegadoException}). M8 reemplaza este bean
 * por una implementacion sobre la tabla {@code admins} manteniendo el mismo
 * metodo (port y impl, decision de orquestacion — ver Tasks_Tinku_Implementacion
 * T-M8-03/T-M8-06).
 *
 * <p>Corrige T-M9: el principal del JWT es el DNI (no un UUID — ver
 * {@code JwtAuthenticationFilter}/{@code UsuarioDetailsService}), así que el
 * check de pertenencia es contra el DNI del allowlist y el id devuelto (que se
 * persiste en {@code admin_id} de sanciones/denuncias) es el UUID del usuario
 * resuelto — el {@code UUID.fromString(dni)} original rompía incluso para
 * admins legítimos.</p>
 */
@Component
public class AdminModeracionGate {

    private final Set<String> ids;
    private final UsuarioRepository usuarioRepository;

    public AdminModeracionGate(@Value("${tinku.admin.moderacion.ids:}") String ids,
                               UsuarioRepository usuarioRepository) {
        this.ids = ids == null || ids.isBlank() ? Set.of() : Set.of(ids.split("\\s*,\\s*"));
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Devuelve el UUID del Admin de Moderacion autenticado o lanza
     * {@link AccesoModeracionDenegadoException} (403): si no autenticado, si su
     * DNI no está en el allowlist, o si el DNI no pertenece a un usuario real.
     */
    public UUID requiereModeracion(Authentication authentication) {
        String nombre = authentication == null ? null : authentication.getName();
        if (nombre == null || !ids.contains(nombre)) {
            throw new AccesoModeracionDenegadoException();
        }
        return usuarioRepository.findByDni(nombre)
                .map(Usuario::getId)
                .orElseThrow(AccesoModeracionDenegadoException::new);
    }
}