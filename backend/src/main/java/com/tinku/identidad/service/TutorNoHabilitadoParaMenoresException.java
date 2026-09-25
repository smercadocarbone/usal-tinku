package com.tinku.identidad.service;

/**
 * FR-ID-026: el Tutor no tiene un CAP aprobado y vigente, así que no puede dar clases a
 * menores. 409. El mensaje no expone el estado del certificado (T03 §2.3).
 */
public class TutorNoHabilitadoParaMenoresException extends RuntimeException {
    public TutorNoHabilitadoParaMenoresException() {
        super("Este tutor todavía no está habilitado para dar clases a menores.");
    }
}
