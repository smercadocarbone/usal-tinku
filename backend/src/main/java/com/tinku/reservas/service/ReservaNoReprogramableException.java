package com.tinku.reservas.service;

/** US-5: solo las Reservas confirmadas (pagadas) se pueden reprogramar. */
public class ReservaNoReprogramableException extends RuntimeException {
    public ReservaNoReprogramableException() {
        super("Solo se le puede cambiar el horario a una clase confirmada.");
    }
}