package com.tinku.reservas.service;

/** US-5: solo quien pagó la Reserva puede reprogramarla (Artículo II — un menor nunca). */
public class SoloPagadorReservaException extends RuntimeException {
    public SoloPagadorReservaException() {
        super("Solo quien pagó la Reserva puede reprogramarla (Artículo II).");
    }
}