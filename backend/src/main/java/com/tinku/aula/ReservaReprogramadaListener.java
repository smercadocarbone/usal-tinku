package com.tinku.aula;

import com.tinku.reservas.evento.ReservaReprogramadaEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * M3 ← M4 ({@code reserva.reprogramada}): a una Reserva confirmada se le cambió
 * el horario; su Sesión derivada y sus 3 jobs de Quartz (sala/no-show/corte)
 * quedaron agendados al horario VIEJO. Este listener los desagenda y los vuelve a
 * agendar al NUEVO horario (recalculando la duración de la franja). Sin esto, el
 * no-show dispararía sobre una Reserva válida y marcaria {@code no_show_doble}.
 * Corre en la misma transacción de la reprogramación (fail-closed).
 */
@Component
public class ReservaReprogramadaListener {

    private final SesionService sesionService;

    public ReservaReprogramadaListener(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @EventListener
    @Transactional
    public void onReservaReprogramada(ReservaReprogramadaEvent evento) {
        sesionService.reprogramarSesionProgramada(evento.getReservaId());
    }
}