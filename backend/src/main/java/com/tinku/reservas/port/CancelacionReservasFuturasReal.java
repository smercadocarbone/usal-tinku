package com.tinku.reservas.port;

import com.tinku.identidad.port.CancelacionReservasFuturas;
import com.tinku.reservas.service.ReservaService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Implementación real del puerto {@code CancelacionReservasFuturas} (M4), que M1
 *  usa en la baja confirmada de un menor (FR-ID-014). */
@Component
public class CancelacionReservasFuturasReal implements CancelacionReservasFuturas {

    private final ReservaService reservaService;

    public CancelacionReservasFuturasReal(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @Override
    public int cancelarFuturasDeMenor(UUID menorId, UUID adultoResponsableId) {
        return reservaService.cancelarFuturasPorBajaDeMenor(menorId, adultoResponsableId);
    }
}
