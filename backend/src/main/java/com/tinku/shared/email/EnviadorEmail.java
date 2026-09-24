package com.tinku.shared.email;

/**
 * Puerto de envío de email (ADR-000-06). Cambiar de proveedor es cambiar este bean.
 * Fail-closed: si {@link #configurado()} es false, nadie debe llamar a {@link #enviar}
 * — el aviso queda pendiente en el outbox, nunca "enviado" en falso.
 */
public interface EnviadorEmail {

    boolean configurado();

    /** @throws EmailEnvioException si el proveedor no lo aceptó. */
    void enviar(MensajeEmail mensaje);
}
