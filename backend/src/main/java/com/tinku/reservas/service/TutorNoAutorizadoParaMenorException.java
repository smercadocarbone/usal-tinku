package com.tinku.reservas.service;

/** El menor solo puede pedir sesión con un Tutor autorizado por su AR y no marcado
 * no confiable (FR-RES-021, mismo criterio de FR-MATCH-004). */
public class TutorNoAutorizadoParaMenorException extends RuntimeException {
    public TutorNoAutorizadoParaMenorException() {
        super("El Tutor no está autorizado por tu Adulto Responsable para este menor.");
    }
}