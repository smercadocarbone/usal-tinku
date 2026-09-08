package com.tinku.pagos.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code sesion.finalizada} (M3 → M5, Spec_M5 §2): la Sesión terminó con
 * normalidad (o el corte fue después del 50% — M3 emite el mismo evento).
 * M5 inicia la cuenta de 24hs de liberación del escrow al Tutor (FR-PAG-002):
 * la escucha en {@code EscrowService#onSesionFinalizada} fija {@code liberar_at}
 * = {@code timestampFin} + 24h (el job real de liberación es Chunk M5-C).
 */
public class SesionFinalizadaEvent extends SesionEvento {

    private final Instant timestampFin;

    public SesionFinalizadaEvent(Object source, UUID reservaId, Instant timestampFin) {
        super(source, "sesion.finalizada", reservaId);
        this.timestampFin = timestampFin;
    }

    public Instant getTimestampFin() {
        return timestampFin;
    }
}