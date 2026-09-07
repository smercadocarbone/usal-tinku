package com.tinku.reservas.service;

/** Acción restringida a cuentas con la capacidad de Adulto Responsable activa (Artículo II). */
public class SoloAdultoResponsableException extends RuntimeException {
    public SoloAdultoResponsableException(String mensaje) {
        super(mensaje);
    }
}