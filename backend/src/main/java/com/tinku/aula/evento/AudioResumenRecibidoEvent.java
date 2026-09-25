package com.tinku.aula.evento;

import java.util.UUID;

/**
 * ADR-M3-04 (T08): llegó el audio de una sesión con el adicional de resumen. Lo consume M6
 * para disparar la generación (el resumen espera al audio, no se adelanta). No extiende
 * {@link SesionEvento} a propósito: no es una transición de la Sesión y no debe llegar a los
 * listeners genéricos de eventos de sesión.
 */
public record AudioResumenRecibidoEvent(UUID sesionId) {
}
