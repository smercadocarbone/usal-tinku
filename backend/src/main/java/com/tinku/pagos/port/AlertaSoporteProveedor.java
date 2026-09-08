package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;

/**
 * Alerta al Admin de Soporte Financiero por una liberación de escrow que falla
 * ante MercadoPago (FR-PAG-007). El job de liberación (Chunk M5-C) la llama en
 * paralelo al reintento automático; si los 3 reintentos (5min/15min/1h) fallan,
 * la transacción queda en {@code retenido_escrow} con {@code intentos_liberacion}
 * agotados — esa es la cola de intervención manual (endpoint del Spec M8).
 *
 * M5-C: implementación stub (log) — M8 provée la cola real de Soporte Financiero
 * y reemplaza este bean. Nunca lanza: la alerta no puede tumbar el avance de un
 * escrow cuyo estado ya es terminal.
 */
public interface AlertaSoporteProveedor {

    void notificarFalloLiberacion(Transaccion transaccion);
}