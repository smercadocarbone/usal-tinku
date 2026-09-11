package com.tinku.matching;

/** Solo perfiles de Tutor cargan materias/nivel de matching (FR-MATCH-006). */
public class PerfilMatchingTutorRequeridoException extends RuntimeException {
    public PerfilMatchingTutorRequeridoException() {
        super("Solo un Tutor puede configurar su perfil de matching.");
    }
}