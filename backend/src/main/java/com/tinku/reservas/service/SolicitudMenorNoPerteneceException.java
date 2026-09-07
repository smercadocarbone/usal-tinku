package com.tinku.reservas.service;

/** El Adulto Responsable intentó aprobar una Solicitud de un menor que no está a su cargo (FR-ID-020). */
public class SolicitudMenorNoPerteneceException extends RuntimeException {
    public SolicitudMenorNoPerteneceException() {
        super("La Solicitud pertenece a un menor que no está a cargo de esta cuenta.");
    }
}