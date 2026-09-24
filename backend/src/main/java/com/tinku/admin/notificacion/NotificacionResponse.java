package com.tinku.admin.notificacion;

import com.tinku.shared.notificacion.TipoNotificacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Aviso de la bandeja in-app (FASE2-03). {@code datos} ya viene filtrado en origen. */
public record NotificacionResponse(UUID id, TipoNotificacion tipo, Map<String, String> datos,
                                   Instant creadaAt, boolean leida) {

    public static NotificacionResponse from(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getTipo(), n.getDatos(), n.getCreadaAt(), n.getLeidaAt() != null);
    }
}
