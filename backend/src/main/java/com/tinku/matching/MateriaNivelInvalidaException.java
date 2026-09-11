package com.tinku.matching;

/** La (nivel, materia) elegida no existe en el catálogo cerrado (FR-MATCH-006). */
public class MateriaNivelInvalidaException extends RuntimeException {
    public MateriaNivelInvalidaException(String nivel, String materia) {
        super("La materia '" + materia + "' del nivel '" + nivel + "' no está en el catálogo.");
    }
}