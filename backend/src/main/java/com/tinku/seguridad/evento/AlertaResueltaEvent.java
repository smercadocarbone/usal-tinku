package com.tinku.seguridad.evento;

import com.tinku.aula.evento.SesionEvento;

import java.util.UUID;

/**
 * {@code alerta.resuelta} (M9 → M5, Spec_M5 §2, ADR-M3-02): el Admin resolvió la
 * Alerta de Seguridad de la sesión de esta Reserva. El kill-switch dejó el escrow en
 * {@code pausado_alerta} (FASE2-10/AUD-005); con este evento M5 reembolsa el total
 * al Estudiante, sea cual sea la decisión ({@code reactivar} o {@code sancionar}).
 * Payload mínimo (reservaId), mismo patrón que {@link DenunciaRegistradaEvent}.
 */
public class AlertaResueltaEvent extends SesionEvento {

    public AlertaResueltaEvent(Object source, UUID reservaId) {
        super(source, "alerta.resuelta", reservaId);
    }
}
