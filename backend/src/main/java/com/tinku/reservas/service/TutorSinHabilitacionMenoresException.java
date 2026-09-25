package com.tinku.reservas.service;

/** FR-ID-026: el Tutor no tiene CAP aprobado y vigente → no toma clases con menores (409). */
public class TutorSinHabilitacionMenoresException extends RuntimeException {
    public TutorSinHabilitacionMenoresException() {
        super("Este tutor todavía no está habilitado para dar clases a menores.");
    }
}
