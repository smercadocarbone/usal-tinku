package com.tinku.reputacion.service;

public class ComentarioNoPermitidoException extends RuntimeException {
    public ComentarioNoPermitidoException() {
        super("El comentario solo aplica a la calificacion publica del Estudiante hacia el Tutor.");
    }
}