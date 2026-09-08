package com.tinku.aula;

/** No existe la Sesión de Aprendizaje indicada (404). */
public class SesionNoEncontradaException extends RuntimeException {
    public SesionNoEncontradaException() {
        super("Sesión de Aprendizaje no encontrada.");
    }
}