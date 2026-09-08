package com.tinku.reservas.port;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Stub de {@link ReputacionBloqueoProveedor} (FR-REP-006, Chunk M4-E): ningún
 * Tutor bloqueado porque M7 no existe aún. Sin bloqueos, la creación de
 * Reservas no cambia su comportamiento. El Chunk M7-C lo reemplaza con la
 * implementación real (calificación pendiente por sesión no calificada).
 */
@Component
public class ReputacionBloqueoProveedorStub implements ReputacionBloqueoProveedor {

    @Override
    public Set<UUID> tutoresConCalificacionPendiente() {
        return Set.of();
    }
}