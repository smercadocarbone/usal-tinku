package com.tinku.aula.evento;

import java.util.UUID;

/**
 * {@code sesion.no_show_doble} (US-7, Plan M5) — ninguno de los dos se unió.
 * M5 dispone el reembolso total inmediato, sin liberar nada al Tutor.
 */
public class SesionNoShowDobleEvent extends SesionEvento {

    public SesionNoShowDobleEvent(Object source, UUID sesionId, UUID reservaId) {
        super(source, "sesion.no_show_doble", sesionId, reservaId);
    }
}