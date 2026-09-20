package com.tinku.resumen.service;

/** No existe la Sesión de Aprendizaje indicada (404) — mismo criterio que
 * {@code CalificacionSesionNoEncontradaException} de M7. */
public class ResumenSesionNoEncontradaException extends RuntimeException {
    public ResumenSesionNoEncontradaException() {
        super("Sesión de Aprendizaje no encontrada.");
    }
}
