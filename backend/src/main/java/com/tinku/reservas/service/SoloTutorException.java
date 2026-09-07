package com.tinku.reservas.service;

/** Solo los perfiles de Tutor publican franjas de disponibilidad (FR-RES-012). */
public class SoloTutorException extends RuntimeException {
    public SoloTutorException(String mensaje) {
        super(mensaje);
    }
}