package com.tinku.reservas.service;

/** AUD-025: la franja nueva se pisa con otra activa del mismo Tutor (el horario ya está publicado). */
public class FranjaSuperpuestaException extends RuntimeException {
    public FranjaSuperpuestaException() {
        super("Ya tenés una franja publicada que se superpone con ese horario.");
    }
}
