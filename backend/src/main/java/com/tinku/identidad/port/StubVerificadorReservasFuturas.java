package com.tinku.identidad.port;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Por defecto (hasta que M4 exponga reservas reales): ningún menor tiene
 * reservas futuras. M4 reemplaza este bean por su implementación real.
 */
@Component
public class StubVerificadorReservasFuturas implements VerificadorReservasFuturas {
    @Override
    public long contarReservasFuturas(UUID menorId) {
        return 0;
    }
}
