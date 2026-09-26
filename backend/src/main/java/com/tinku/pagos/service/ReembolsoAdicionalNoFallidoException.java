package com.tinku.pagos.service;

/** R4: solo un reembolso del adicional FALLIDO se reintenta o se resuelve a mano (422). */
public class ReembolsoAdicionalNoFallidoException extends RuntimeException {
    public ReembolsoAdicionalNoFallidoException() {
        super("El reembolso del adicional no está en la cola de fallidos.");
    }
}
