package com.tinku.reservas.service;

/** El menor ya tiene una Solicitud pendiente idéntica (mismo tutor y horario). */
public class SolicitudDuplicadaException extends RuntimeException {
    public SolicitudDuplicadaException() {
        super("Ya existe una Solicitud pendiente con el mismo Tutor y horario.");
    }
}