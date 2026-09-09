package com.tinku.identidad.service;

import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Lógica compartida de conteo de intentos dentro de un ciclo de rechazos
 * (FR-ID-008 / FR-ID-023, {@code MAX_INTENTOS_CICLO = 3}).
 */
final class CicloIntentos {

    static final int MAX = 3;

    private CicloIntentos() {}

    /**
     * Devuelve el número de intento para el ciclo actual: 1 si el ciclo arranca
     * o no hay historial, o el siguiente tras un rechazo dentro del ciclo.
     */
    static <T> int siguiente(Optional<T> ultimo,
                             java.util.function.Predicate<T> esRechazado,
                             ToIntFunction<T> numeroIntento) {
        return ultimo
                .filter(e -> esRechazado.test(e) && numeroIntento.applyAsInt(e) < MAX)
                .map(e -> numeroIntento.applyAsInt(e) + 1)
                .orElse(1);
    }
}
