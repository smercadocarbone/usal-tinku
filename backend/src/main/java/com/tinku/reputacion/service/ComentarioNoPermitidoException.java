package com.tinku.reputacion.service;

public class ComentarioNoPermitidoException extends RuntimeException {
    public ComentarioNoPermitidoException() {
        super("El comentario solo aplica a la calificación pública del Estudiante hacia el Tutor.");
    }
}