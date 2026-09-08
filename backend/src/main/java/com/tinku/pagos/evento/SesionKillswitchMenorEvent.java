package com.tinku.pagos.evento;

import java.util.UUID;

/**
 * {@code sesion.killswitch_menor} (M3 → M5, Spec_M5 §2): corte por contenido
 * inapropiado/ilegal con un MENOR presente (BR-KS-01). M5 reembolsa el total al
 * Estudiante (FR-PAG-009) — nunca el menor confirma nada por sí solo (Artículo
 * II, la sesión corta directo).
 */
public class SesionKillswitchMenorEvent extends SesionEvento {

    public SesionKillswitchMenorEvent(Object source, UUID reservaId) {
        super(source, "sesion.killswitch_menor", reservaId);
    }
}