package com.tinku.reputacion.service;

public class CalificacionOcultaNoEditableException extends RuntimeException {
    public CalificacionOcultaNoEditableException() {
        super("Solo la calificacion publica (estudiante_a_tutor) es editable o eliminable.");
    }
}