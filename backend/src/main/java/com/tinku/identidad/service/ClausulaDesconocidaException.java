package com.tinku.identidad.service;

public class ClausulaDesconocidaException extends RuntimeException {

    public ClausulaDesconocidaException() {
        super("Cláusula desconocida.");
    }
}
