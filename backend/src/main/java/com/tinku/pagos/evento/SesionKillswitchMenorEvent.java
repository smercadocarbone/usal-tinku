package com.tinku.pagos.evento;

import java.util.UUID;

/**
 * {@code sesion.killswitch_menor} (M3 → M5, Spec_M5 §2): corte por contenido
 * inapropiado/ilegal con un MENOR presente (BR-KS-01). M5 reembolsa el total al
 * Estudiante (FR-PAG-009) — nunca el menor confirma nada por sí solo (Artículo
 * II, la sesión corta directo). {@code detectadoId} identifica al usuario cuyo
 * contenido fue detectado (para suspensión preventiva y revisión de M9).
 */
public class SesionKillswitchMenorEvent extends SesionEvento {

    private final UUID detectadoId;

    public SesionKillswitchMenorEvent(Object source, UUID reservaId, UUID detectadoId) {
        super(source, "sesion.killswitch_menor", reservaId);
        this.detectadoId = detectadoId;
    }

    public UUID getDetectadoId() {
        return detectadoId;
    }
}