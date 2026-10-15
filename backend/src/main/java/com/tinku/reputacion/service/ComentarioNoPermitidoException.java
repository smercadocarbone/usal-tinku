package com.tinku.reputacion.service;

public class ComentarioNoPermitidoException extends RuntimeException {
    public ComentarioNoPermitidoException() {
        super("El comentario solo se puede dejar en la calificación del alumno al tutor.");
    }
}