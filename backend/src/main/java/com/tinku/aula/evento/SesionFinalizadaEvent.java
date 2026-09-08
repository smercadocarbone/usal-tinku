package com.tinku.aula.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code sesion.finalizada} (US-8, Plan M3 §3.5) — la sesión cerró con normalidad
 * (botón «Finalizar» o corte automático a T-fin+5). M5 lo usa para programar la
 * liberación del escrow a {@code timestamp_fin + 24h}; M6 valida {@code duracionEfectivaSegundos}
 * contra el umbral de 10 min.
 */
public class SesionFinalizadaEvent extends SesionEvento {

    private final Instant finReal;
    private final int duracionEfectivaSegundos;

    public SesionFinalizadaEvent(Object source, UUID sesionId, UUID reservaId,
                                 Instant finReal, int duracionEfectivaSegundos) {
        super(source, "sesion.finalizada", sesionId, reservaId);
        this.finReal = finReal;
        this.duracionEfectivaSegundos = duracionEfectivaSegundos;
    }

    public Instant getFinReal() {
        return finReal;
    }

    public int getDuracionEfectivaSegundos() {
        return duracionEfectivaSegundos;
    }
}