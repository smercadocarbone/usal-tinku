package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;

/**
 * Liberación del escrow al Tutor (Plan M5 §3.2, FR-PAG-002/007). El split quedó
 * definido en la preferencia original (marketplace_fee, M5-A, Split Payments
 * 1:1): liberar NO es una segunda transacción, es confirmar que el pago sigue
 * vigente a la hora de liberar.
 *
 * Chunk M5-C: la implementación real es {@link LiberacionProveedorMercadoPago}
 * (verifica contra GET /v1/payments/{id}); si falla, el job de liberación la
 * re-intenta con backoff (FR-PAG-007) — fail-closed: nunca marcar {@code liberado}
 * sin confirmación del provider.
 */
public interface LiberacionProveedor {

    void liberarAlTutor(Transaccion transaccion);
}