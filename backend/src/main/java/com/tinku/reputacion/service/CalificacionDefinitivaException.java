package com.tinku.reputacion.service;

public class CalificacionDefinitivaException extends RuntimeException {
    public CalificacionDefinitivaException() {
        super("La calificacion vencio su ventana de edicion de 48hs (FR-REP-005).");
    }
}