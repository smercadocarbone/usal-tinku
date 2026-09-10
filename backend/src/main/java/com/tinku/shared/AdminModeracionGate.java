package com.tinku.shared;

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
 */
@Component
public class AdminModeracionGate {

    private final Set<String> ids;

    public AdminModeracionGate(@Value("${tinku.admin.moderacion.ids:}") String ids) {
        this.ids = ids == null || ids.isBlank() ? Set.of() : Set.of(ids.split("\\s*,\\s*"));
    }

    /**
     * Devuelve el id (UUID en el principal del Admin de Moderacion autenticado)
     * o lanza {@link AccesoModeracionDenegadoException} (403).
     */
    public UUID requiereModeracion(Authentication authentication) {
        String nombre = authentication == null ? null : authentication.getName();
        if (nombre == null || !ids.contains(nombre)) {
            throw new AccesoModeracionDenegadoException();
        }
        try {
            return UUID.fromString(nombre);
        } catch (IllegalArgumentException e) {
            throw new AccesoModeracionDenegadoException();
        }
    }
}