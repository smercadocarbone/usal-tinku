package com.tinku.reservas.service;

/** El menor ya tiene una Solicitud pendiente idéntica (mismo tutor y horario). */
public class SolicitudDuplicadaException extends RuntimeException {
    public SolicitudDuplicadaException() {
        super("Ya pediste una clase con este tutor en ese horario y está esperando respuesta.");
    }
}