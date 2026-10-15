package com.tinku.aula;

/**
 * Solo participan de la Sesión el tutor, el beneficiario/estudiante o el pagador de la Reserva
 * que la originó — nunca un tercero (403). R-B B-8 (T6-4): el mensaje nombra la acción que se
 * intentó (antes decía "finalizarla" en todos los casos).
 */
public class SoloParticipanteException extends RuntimeException {
    public SoloParticipanteException() {
        this("finalizarla");
    }

    /** {@code accion}: lo que sigue a "pueden", p. ej. "verla" o "entrar a ella". */
    public SoloParticipanteException(String accion) {
        super("Solo los participantes de la sesión pueden " + accion + ".");
    }
}
