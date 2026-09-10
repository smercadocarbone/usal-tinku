package com.tinku.shared;

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
 * Moderacion es una lista permitida de DNIs in {@code tinku.admin.moderacion.ids}
 * (fail-closed: sin configurar, toda llamada → {@link AccesoModeracionDenegadoException}).
 * El principal del JWT lleva el DNI (UsuarioDetailsService), asi que el gate
 * valida el DNI contra la lista Y resuelve el UUID del Usuario para devolverlo
 * (un DNI no es un UUID — ese fue el primer bug que destapo M7 en este gate;
 * antes lanzaba 403 siempre). M8 reemplaza este bean por una implementacion
 * sobre la tabla {@code admins} manteniendo el mismo metodo (port y impl,
 * decision de orquestacion — ver Tasks_Tinku_Implementacion T-M8-03/T-M8-06).
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
     * Devuelve el id (UUID del Usuario autenticado) o lanza
     * {@link AccesoModeracionDenegadoException} (403).
     */
    public UUID requiereModeracion(Authentication authentication) {
        String dni = authentication == null ? null : authentication.getName();
        if (dni == null || !ids.contains(dni.trim())) {
            throw new AccesoModeracionDenegadoException();
        }
        return usuarioRepository.findByDni(dni)
                .map(u -> u.getId())
                .orElseThrow(AccesoModeracionDenegadoException::new);
    }
}