package com.tinku.reputacion.service;

public class CalificacionNoEncontradaException extends RuntimeException {
    public CalificacionNoEncontradaException() {
        super("La calificación no existe.");
    }
}