package com.tinku.aula.evento;

import java.util.UUID;

/**
 * {@code sesion.no_show_tutor} (US-7, Plan M5) — el tutor no se unió; el
 * estudiante sí. M5 dispone el reembolso total inmediato al pagador.
 */
public class SesionNoShowTutorEvent extends SesionEvento {

    public SesionNoShowTutorEvent(Object source, UUID sesionId, UUID reservaId) {
        super(source, "sesion.no_show_tutor", sesionId, reservaId);
    }
}