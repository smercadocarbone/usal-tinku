package com.tinku.identidad.service;

public class EdadInsuficienteException extends RuntimeException {
    public EdadInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
