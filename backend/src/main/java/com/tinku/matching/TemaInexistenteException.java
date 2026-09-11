package com.tinku.matching;

/** Un id de tema es un UUID válido pero no pertenece al catálogo vigente
 * (FR-MATCH-006): 404, el catálogo es cerrado y curado. */
public class TemaInexistenteException extends RuntimeException {

    public TemaInexistenteException() {
        super("Uno o más temas no pertenecen al catálogo vigente.");
    }
}