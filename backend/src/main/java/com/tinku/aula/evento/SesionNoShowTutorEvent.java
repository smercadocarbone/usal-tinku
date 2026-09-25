package com.tinku.aula.evento;

import java.util.UUID;

/**
 * {@code sesion.no_show_tutor} (M3/M4 → M5, Spec_M5 §2): el Tutor no se unió a la
 * sesión. M5 reembolsa el total al Estudiante (FR-RES-005 de M4, FR-PAG-004).
 */
public class SesionNoShowTutorEvent extends SesionEvento {

    public SesionNoShowTutorEvent(Object source, UUID reservaId) {
        super(source, "sesion.no_show_tutor", reservaId);
    }
}