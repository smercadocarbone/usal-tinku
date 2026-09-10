package com.tinku.reputacion.service;

public class CalificacionNoEncontradaException extends RuntimeException {
    public CalificacionNoEncontradaException() {
        super("La calificacion no existe.");
    }
}