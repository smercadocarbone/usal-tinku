package com.tinku.shared.notificacion;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de avisos a usuarios (FASE2-03, AUD-014). Sin tipos de dominio en la firma
 * (nada de {@code Usuario}/{@code Reserva}): así {@code shared} no suma dependencias
 * hacia los módulos (ADR-000-03, AUD-019).
 *
 * <p>Contrato de outbox: se llama DENTRO de la transacción del hecho que origina el
 * aviso; si esa transacción se revierte, el aviso no existe. {@code datos} viaja a la
 * bandeja del destinatario tal cual: nunca pongas ahí nada que él no deba ver.</p>
 */
public interface Notificador {

    void notificar(UUID destinatarioId, TipoNotificacion tipo, Map<String, String> datos);
}
