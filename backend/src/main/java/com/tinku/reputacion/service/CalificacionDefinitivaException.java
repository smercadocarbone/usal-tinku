package com.tinku.reputacion.service;

public class CalificacionDefinitivaException extends RuntimeException {
    public CalificacionDefinitivaException() {
        super("La calificación venció: pasaron más de 48 horas y ya no se puede cambiar.");
    }
}