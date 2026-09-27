package com.tinku.reservas.service;

/** FR-RES-027: el pedido previo no puede quedar vacío. */
public class PedidoPrevioVacioException extends RuntimeException {
    public PedidoPrevioVacioException() {
        super("Escribí qué querés ver en la clase.");
    }
}
