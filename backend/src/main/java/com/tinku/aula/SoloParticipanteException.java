package com.tinku.aula;

/**
 * Solo participan de la Sesión (pueden finalizarla, US-8) el tutor, el
 * beneficiario/estudiante o el pagador de la Reserva que la originó — nunca un
 * tercero (403).
 */
public class SoloParticipanteException extends RuntimeException {
    public SoloParticipanteException() {
        super("Solo los participantes de la sesión pueden finalizarla.");
    }
}