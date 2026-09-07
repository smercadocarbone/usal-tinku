package com.tinku.identidad.service;

/**
 * Se intentó operar sobre un perfil de menor que no está a cargo del Adulto
 * Responsable autenticado (FR-ID-020): un AR solo puede autorizar/bajar a sus
 * propios menores.
 */
public class MenorNoPerteneceException extends RuntimeException {
    public MenorNoPerteneceException() {
        super("El perfil de menor no está a tu cargo.");
    }
}
