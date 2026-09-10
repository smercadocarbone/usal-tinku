package com.tinku.identidad.port;

import com.tinku.identidad.dto.ReputacionTutor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub de {@link ReputacionPerfilProvider}: M7 todavía no existe (mismo patrón
 * que ReputacionSignalProviderStub/ReputacionBloqueoProveedor) — sin
 * calificaciones, el promedio queda {@code null} (FR-REP-007) y la cantidad en
 * cero.
 */
@Primary
@Component
public class ReputacionPerfilProviderStub implements ReputacionPerfilProvider {

    @Override
    public ReputacionTutor reputacion(UUID tutorId) {
        return new ReputacionTutor(null, 0);
    }
}