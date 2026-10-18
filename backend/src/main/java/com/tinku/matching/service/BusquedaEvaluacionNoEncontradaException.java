package com.tinku.matching.service;

/** T-TES-11b: la búsqueda no existe o es de otro usuario → 404 (no se distingue cuál). */
public class BusquedaEvaluacionNoEncontradaException extends RuntimeException {
    public BusquedaEvaluacionNoEncontradaException() {
        super("No encontramos esa búsqueda.");
    }
}
