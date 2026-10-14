package com.tinku.aula.web;

import com.tinku.aula.SesionService;

import java.time.Instant;
import java.util.UUID;

/**
 * Estado del kill-switch para el cliente (ADR-M3-07): sirve para elegir el umbral y para que
 * quien recarga la página a mitad de la pregunta de la rama adultos la retome. Nada de la Alerta
 * (BR-KS-03 la deja en M9).
 */
public record EstadoKillswitchResponse(boolean hayMenor, boolean confirmacionPendiente,
                                       UUID detectadoId, Instant venceAt) {

    public static EstadoKillswitchResponse from(SesionService.EstadoKillswitch e) {
        return new EstadoKillswitchResponse(e.hayMenor(), e.confirmacionPendiente(), e.detectadoId(), e.venceAt());
    }
}
