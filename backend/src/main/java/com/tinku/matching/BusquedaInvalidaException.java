package com.tinku.matching;

/** POST /api/busquedas: al menos uno de texto_busqueda / nombre /
 * filtro_materia debe venir no vacío (contrato 2b). */
public class BusquedaInvalidaException extends RuntimeException {

    public BusquedaInvalidaException() {
        super("La búsqueda debe incluir texto, nombre de tema o materia.");
    }
}