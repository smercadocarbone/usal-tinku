package com.tinku.reservas.service;

/** FR-RES-026: nota fuera de una clase finalizada con un Menor, vacía o fuera de las 48 hs. */
public class NotaClaseNoPermitidaException extends RuntimeException {
    public NotaClaseNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
