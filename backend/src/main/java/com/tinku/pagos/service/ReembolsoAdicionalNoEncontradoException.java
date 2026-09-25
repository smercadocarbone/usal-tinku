package com.tinku.pagos.service;

/** R4: la transacción del reembolso del adicional no existe (404). */
public class ReembolsoAdicionalNoEncontradoException extends RuntimeException {
    public ReembolsoAdicionalNoEncontradoException() {
        super("No existe esa transacción.");
    }
}
