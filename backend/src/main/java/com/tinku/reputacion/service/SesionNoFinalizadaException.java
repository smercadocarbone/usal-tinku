package com.tinku.reputacion.service;

public class SesionNoFinalizadaException extends RuntimeException {
    public SesionNoFinalizadaException() {
        super("Solo se califican Sesiones finalizadas (FR-REP-008).");
    }
}