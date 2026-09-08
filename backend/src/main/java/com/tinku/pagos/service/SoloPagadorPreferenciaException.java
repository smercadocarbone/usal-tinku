package com.tinku.pagos.service;

/** Us-1 (Artículo II): solo quien paga la Reserva puede generar su preferencia
 * de pago — un menor nunca, el Tutor tampoco. */
public class SoloPagadorPreferenciaException extends RuntimeException {
    public SoloPagadorPreferenciaException() {
        super("Solo quien paga la Reserva puede generar la preferencia de pago (Artículo II).");
    }
}