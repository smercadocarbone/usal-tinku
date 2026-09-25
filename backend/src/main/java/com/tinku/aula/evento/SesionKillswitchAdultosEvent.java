package com.tinku.aula.evento;

import java.util.UUID;

/**
 * {@code sesion.killswitch_adultos} (M3 → M5, Spec_M5 §2): corte por conducta
 * inapropiada en la rama de ambos adultos (US-7 de M3), con {@code detectadoId} =
 * quién generó la detección. M5 reembolsa SIEMPRE el total al Estudiante, incluso
 * si {@code detectadoId} es el propio pagador (FR-PAG-012): la plataforma no usa
 * el dinero como castigo, la sanción real la aplica M9 (FR-SEC-012). Por eso el
 * listener ignora {@code detectadoId} a propósito.
 */
public class SesionKillswitchAdultosEvent extends SesionEvento {

    private final UUID detectadoId;

    public SesionKillswitchAdultosEvent(Object source, UUID reservaId, UUID detectadoId) {
        super(source, "sesion.killswitch_adultos", reservaId);
        this.detectadoId = detectadoId;
    }

    public UUID getDetectadoId() {
        return detectadoId;
    }
}