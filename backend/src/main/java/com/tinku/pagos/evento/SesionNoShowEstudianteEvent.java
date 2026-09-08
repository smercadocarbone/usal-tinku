package com.tinku.pagos.evento;

import java.util.UUID;

/**
 * {@code sesion.no_show_estudiante} (M3/M4 → M5, Spec_M5 §2): el Estudiante no se
 * unió a la sesión. Se cobra igual y se LIBERA el escrow al Tutor de inmediato
 * (no espera las 24hs — el no-show ya confirma que la sesión no ocurrió,
 * FR-RES-005 de M4, Plan M5 §2).
 */
public class SesionNoShowEstudianteEvent extends SesionEvento {

    public SesionNoShowEstudianteEvent(Object source, UUID reservaId) {
        super(source, "sesion.no_show_estudiante", reservaId);
    }
}