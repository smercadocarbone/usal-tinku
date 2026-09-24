package com.tinku.identidad.service;

/** U1: solo un Tutor tiene perfil público que editar. */
public class SoloTutorException extends RuntimeException {
    public SoloTutorException() {
        super("Solo los tutores tienen un perfil público.");
    }
}
