package com.tinku.pagos.evento;

import java.util.UUID;

/**
 * {@code sesion.interrumpida} (M3 → M5, Spec_M5 §2): corte total de la sesión
 * antes del 50% de la duración agendada (FR-AULA-005). M5 reembolsa el total al
 * Estudiante (FR-PAG-004).
 */
public class SesionInterrumpidaEvent extends SesionEvento {

    public SesionInterrumpidaEvent(Object source, UUID reservaId) {
        super(source, "sesion.interrumpida", reservaId);
    }
}