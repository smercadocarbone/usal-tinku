package com.tinku.admin.service;

/**
 * 422 al contactar a soporte con un {@code origen_modulo} sin mapeo: el ticket
 * no puede enrutarse y no se persiste (fail-closed — un ticket huérfano sería
 * una cola sin dueño).
 */
public class OrigenMapNoDefinidoException extends RuntimeException {

    public OrigenMapNoDefinidoException(String origenModulo) {
        super("Origen de soporte no configurado: " + origenModulo);
    }
}