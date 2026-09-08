package com.tinku.aula;

import com.tinku.reservas.evento.ReservaConfirmadaEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * M3 → M4: al confirmarse una Reserva (pago registrado), crea la Sesión de
 * Aprendizaje y agenda sus jobs de Quartz (T-M3-03/04/05). Corre en la misma
 * transacción de la confirmación: si agendar los jobs falla, la confirmación
 * se aborta — nunca una Reserva confirmada sin sus timeouts.
 */
@Component
public class ReservaConfirmadaListener {

    private final SesionService sesionService;

    public ReservaConfirmadaListener(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @EventListener
    @Transactional
    public void onReservaConfirmada(ReservaConfirmadaEvent evento) {
        sesionService.programarSesion(evento.getReservaId());
    }
}