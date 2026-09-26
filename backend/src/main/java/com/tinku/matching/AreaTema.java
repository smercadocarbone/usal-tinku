package com.tinku.matching;

/**
 * Materia y nivel del catálogo (V19) que el sistema reconoce en una búsqueda sin tutor
 * directo (FR-MATCH-011), a partir del tema del catálogo más parecido a lo escrito.
 */
public record AreaTema(String nivel, String materia) {

    /** "Matemática · Secundario": lo que ve quien buscó. */
    public String rotulo() {
        return materia + " · " + Character.toUpperCase(nivel.charAt(0)) + nivel.substring(1);
    }
}
