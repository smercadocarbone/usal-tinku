package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;

/**
 * Liberación del escrow al Tutor (Plan M5 §3.2, FR-PAG-002/007). El split quedó
 * definido en la preferencia original (marketplace_fee, M5-A): liberar NO es una
 * segunda transacción, es la confirmación de pago de la porción del Tutor.
 *
 * Chunk M5-B: el webhook y los listeners usan este port; la implementación real
 * contra MercadoPago llega en Chunk M5-C (job de liberación) — hasta entonces
 * {@link LiberacionProveedorFailClosed} falla ruidosamente en lugar de registrar
 * un "liberado" que nunca liberó nada.
 */
public interface LiberacionProveedor {

    void liberarAlTutor(Transaccion transaccion);
}