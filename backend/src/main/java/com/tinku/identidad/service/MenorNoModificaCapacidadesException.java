package com.tinku.identidad.service;

/**
 * T3-01 (pruebas en producción, 2026-10-14): las capacidades de un perfil de Menor las administra
 * Tinku; el Menor no las cambia desde su cuenta (Artículo II). → 403.
 */
public class MenorNoModificaCapacidadesException extends RuntimeException {

    public MenorNoModificaCapacidadesException() {
        super("Las capacidades de un perfil de menor las administra Tinku; no se pueden cambiar desde la cuenta del menor.");
    }
}
