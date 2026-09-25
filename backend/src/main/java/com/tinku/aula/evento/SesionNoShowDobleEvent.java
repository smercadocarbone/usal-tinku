package com.tinku.aula.evento;

import java.util.UUID;

/**
 * {@code sesion.no_show_doble} (M3/M4 → M5, Spec_M5 §2): nadie se unió a la
 * sesión. M5 reembolsa el total al Estudiante y NO libera nada al Tutor
 * (FR-RES-009 de M4, FR-PAG-004).
 */
public class SesionNoShowDobleEvent extends SesionEvento {

    public SesionNoShowDobleEvent(Object source, UUID reservaId) {
        super(source, "sesion.no_show_doble", reservaId);
    }
}