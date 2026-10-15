package com.tinku.admin.service;

/** R-B B-11 (T4-H13): un 404 de un endpoint de admin lleva cuerpo {@code {"error"}}, como el resto. */
public class RecursoAdminNoEncontradoException extends RuntimeException {
    public RecursoAdminNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
