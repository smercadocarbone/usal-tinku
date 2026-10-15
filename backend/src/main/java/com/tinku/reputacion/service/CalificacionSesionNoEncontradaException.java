package com.tinku.reputacion.service;

public class CalificacionSesionNoEncontradaException extends RuntimeException {
    public CalificacionSesionNoEncontradaException() {
        super("No encontramos esa clase.");
    }
}