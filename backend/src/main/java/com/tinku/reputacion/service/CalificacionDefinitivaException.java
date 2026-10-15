package com.tinku.reputacion.service;

public class CalificacionDefinitivaException extends RuntimeException {
    public CalificacionDefinitivaException() {
        super("La calificación venció su ventana de edición de 48 hs (FR-REP-005).");
    }
}