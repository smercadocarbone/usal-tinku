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
        super("El Tutor tiene una calificación de Estudiante pendiente de una sesión anterior (FR-REP-006): se bloquea su próxima reserva.");
    }
}