package com.tinku.pagos.service;

/** FASE2-07 / AUD-018: el Modo Bypass (pasarela apagada) no se puede activar con el perfil {@code prod}. */
public class BypassNoPermitidoException extends RuntimeException {
    public BypassNoPermitidoException() {
        super("En producción la pasarela de pagos no se puede apagar: el Modo Bypass es solo para entornos de prueba.");
    }
}
