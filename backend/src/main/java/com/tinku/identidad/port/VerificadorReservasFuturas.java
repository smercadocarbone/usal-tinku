package com.tinku.identidad.port;

import java.util.UUID;

/**
 * Puerto hacia el módulo de Reservas (M4) para la baja de menor (FR-ID-014):
 * antes de eliminar el perfil de un menor hay que saber si tiene sesiones
 * futuras agendadas.
 *
 * M4 implementa este puerto leyendo reservas persistidas. Hasta que exista,
 * el {@code StubVerificadorReservasFuturas} devuelve 0 y la baja nunca pide
 * confirmación adicional (ver NOTAS_VERIFICACION.md de M1-E: la verificación
 * real pendiente).
 */
@FunctionalInterface
public interface VerificadorReservasFuturas {

    /** Cantidad de reservas futuras (fecha de la sesión aún no transcurrida) del menor. */
    long contarReservasFuturas(UUID menorId);
}
