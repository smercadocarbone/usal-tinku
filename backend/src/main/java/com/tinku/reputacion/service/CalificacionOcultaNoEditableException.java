package com.tinku.reputacion.service;

public class CalificacionOcultaNoEditableException extends RuntimeException {
    public CalificacionOcultaNoEditableException() {
        super("Solo se puede cambiar o borrar la calificación pública al tutor.");
    }
}