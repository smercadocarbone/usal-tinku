package com.tinku.reservas.port;

import com.tinku.identidad.port.CancelacionReservasConMenores;
import com.tinku.reservas.service.ReservaService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Implementación real del puerto de M1 para PT10 (T02): cancelar las clases con menores
 *  de un Tutor que perdió la habilitación. */
@Component
public class CancelacionReservasConMenoresReal implements CancelacionReservasConMenores {

    private final ReservaService reservaService;

    public CancelacionReservasConMenoresReal(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @Override
    public int cancelarFuturasConMenores(UUID tutorId) {
        return reservaService.cancelarFuturasConMenoresPorCap(tutorId);
    }
}
