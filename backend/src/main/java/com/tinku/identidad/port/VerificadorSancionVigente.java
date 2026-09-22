package com.tinku.identidad.port;

import java.util.UUID;

/**
 * Puerto hacia M9 (Seguridad) para no reactivar a quien tiene una sanción vigente
 * (AUD-013): aprobar una credencial no puede devolverle el matching a un Tutor
 * suspendido definitivamente. Mismo patrón que {@link VerificadorReservasFuturas}:
 * M1 no importa el repositorio de M9 (AUD-019).
 *
 * Implementación real: {@code com.tinku.seguridad.port.VerificadorSancionVigenteReal}.
 */
@FunctionalInterface
public interface VerificadorSancionVigente {

    /** Definitiva, baneo, o temporal todavía no vencida. */
    boolean tieneSancionVigente(UUID usuarioId);
}
