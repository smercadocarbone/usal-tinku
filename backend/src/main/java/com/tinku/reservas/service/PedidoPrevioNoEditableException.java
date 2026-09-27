package com.tinku.reservas.service;

/** FR-RES-027: el pedido previo se edita solo antes de la clase y con la reserva viva. */
public class PedidoPrevioNoEditableException extends RuntimeException {
    public PedidoPrevioNoEditableException() {
        super("La clase ya empezó o la reserva no está activa: el pedido ya no se puede cambiar.");
    }
}
