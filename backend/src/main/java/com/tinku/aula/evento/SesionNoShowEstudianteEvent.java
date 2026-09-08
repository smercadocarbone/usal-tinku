package com.tinku.aula.evento;

import java.util.UUID;

/**
 * {@code sesion.no_show_estudiante} (US-7, Plan M5) — el estudiante no se unió;
 * el tutor sí. M5 libera el escrow al Tutor de inmediato (no espera las 24hs).
 */
public class SesionNoShowEstudianteEvent extends SesionEvento {

    public SesionNoShowEstudianteEvent(Object source, UUID sesionId, UUID reservaId) {
        super(source, "sesion.no_show_estudiante", sesionId, reservaId);
    }
}