package com.tinku.reservas.service;

/**
 * FR-REP-006 (T-M4-10) — el Tutor no puede tomar nuevas Reservas mientras
 * tenga una calificación de Estudiante pendiente de una sesión anterior. La
 * lista de tutores bloqueados es de M7 (port {@code ReputacionBloqueoProveedor});
 * esto es 403 para que ninguna vía de creación de Reserva (directa o por
 * Solicitud) lo esquive.
 */
public class TutorPendienteCalificacionException extends RuntimeException {

    public TutorPendienteCalificacionException() {
        super("Este tutor tiene que calificar una clase anterior antes de recibir una reserva nueva. Probá más tarde.");
    }
}