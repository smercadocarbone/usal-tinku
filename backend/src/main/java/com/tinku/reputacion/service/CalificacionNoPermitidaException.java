package com.tinku.reputacion.service;

public class CalificacionNoPermitidaException extends RuntimeException {
    public CalificacionNoPermitidaException() {
        super("Solo el tutor, el alumno o quien pagó la clase pueden calificarla.");
    }
}