package com.tinku.reservas.service;

/**
 * Artículo II: una Reserva la crea y paga exclusivamente quien tiene capacidad
 * de pago — Estudiante adulto (para sí mismo) o Adulto Responsable (por su
 * menor). Un menor o un adulto sin capacidad de pago queda prohibido a nivel de
 * servicio/endpoint, no solo escondido en el frontend.
 */
public class CapacidadDePagoRequeridaException extends RuntimeException {
    public CapacidadDePagoRequeridaException() {
        super("Solo quien paga (Estudiante adulto o Adulto Responsable) puede crear una Reserva (Artículo II).");
    }
}