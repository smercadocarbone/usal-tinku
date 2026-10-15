package com.tinku.reputacion.service;

public class SesionNoFinalizadaException extends RuntimeException {
    public SesionNoFinalizadaException() {
        super("Solo se pueden calificar clases terminadas.");
    }
}