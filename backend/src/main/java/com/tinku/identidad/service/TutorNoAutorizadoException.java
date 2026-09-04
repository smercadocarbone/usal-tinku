package com.tinku.identidad.service;

/**
 * Solo la capacidad "Adulto Responsable" puede autorizar a un Tutor o marcarlo
 * como no confiable (FR-ID-009), y solo sobre menores a su cargo. Un perfil de
 * menor o un adulto sin esa capacidad no puede.
 */
public class TutorNoAutorizadoException extends RuntimeException {
    public TutorNoAutorizadoException() {
        super("No estás autorizado para esta operación.");
    }

    public TutorNoAutorizadoException(String mensaje) {
        super(mensaje);
    }
}
