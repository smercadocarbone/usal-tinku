package com.tinku.reservas.service;

/** FR-RES-029..031: el pedido de reprogramación no se puede hacer o responder. */
public class PedidoReprogramacionException extends RuntimeException {
    public PedidoReprogramacionException(String mensaje) {
        super(mensaje);
    }
}
