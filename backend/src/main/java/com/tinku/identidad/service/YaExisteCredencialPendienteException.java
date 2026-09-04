package com.tinku.identidad.service;

/**
 * Un Tutor ya tiene una credencial PENDIENTE de revisión: no puede cargar
 * otra hasta que Admin la resuelva (US-4).
 */
public class YaExisteCredencialPendienteException extends RuntimeException {
    public YaExisteCredencialPendienteException() {
        super("Ya tenés una credencial en revisión. Esperá la respuesta de un administrador.");
    }
}
