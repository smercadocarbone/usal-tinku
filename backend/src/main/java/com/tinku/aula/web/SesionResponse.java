package com.tinku.aula.web;

import com.tinku.aula.model.SesionAprendizaje;

import java.time.Instant;
import java.util.UUID;

public record SesionResponse(UUID id, UUID reservaId, String estado, String livekitRoomId,
                             Instant inicioReal, Instant finReal, Integer duracionEfectivaSegundos) {

    public static SesionResponse from(SesionAprendizaje s) {
        return new SesionResponse(s.getId(), s.getReservaId(), s.getEstado(),
                s.getLivekitRoomId(), s.getInicioReal(), s.getFinReal(),
                s.getDuracionEfectivaSegundos());
    }
}