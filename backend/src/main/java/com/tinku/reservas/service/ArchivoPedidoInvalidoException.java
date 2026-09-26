package com.tinku.reservas.service;

/** ADR-M4-01: archivo del pedido previo fuera de tipo o tamaño, o pedido vacío. */
public class ArchivoPedidoInvalidoException extends RuntimeException {
    public ArchivoPedidoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
