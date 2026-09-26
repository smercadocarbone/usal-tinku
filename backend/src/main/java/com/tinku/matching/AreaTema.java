package com.tinku.matching;

/**
 * Materia y nivel del catálogo (V19) que el sistema reconoce en una búsqueda sin tutor
 * directo (FR-MATCH-011), a partir del tema del catálogo más parecido a lo escrito.
 */
public record AreaTema(String nivel, String materia) {

    /** "Matemática de primario": se lee dentro de una frase ("tutores de Matemática de primario"). */
    public String rotulo() {
        return switch (nivel) {
            case "universitario" -> materia + " de la universidad";
            default -> materia + " de " + nivel;
        };
    }

    AreaTema conNivel(String otroNivel) {
        return new AreaTema(otroNivel, materia);
    }
}
