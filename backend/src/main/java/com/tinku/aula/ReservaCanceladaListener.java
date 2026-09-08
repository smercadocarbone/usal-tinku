package com.tinku.aula;

import com.tinku.reservas.evento.ReservaCanceladaEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * M3 ← M4 ({@code reserva.cancelada}): se canceló manualmente una Reserva
 * confirmada que ya había creado su Sesión y agendado los 3 jobs de Quartz
 * la Sesión derivada y agendado los 3 jobs de Quartz (sala/no-show/corte). Este
 * listener los desagenda — es limpieza: los jobs ya son no-op si la Reserva dejó
 * de estar confirmada (guard de {@code SesionService}), pero no dejamos disparos
 * muertos agendados. Corre en la misma transacción de la cancelación; si fallara
 * algo, la cancelación se aborta (fail-closed).
 */
@Component
public class ReservaCanceladaListener {

    private final SesionService sesionService;

    public ReservaCanceladaListener(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @EventListener
    @Transactional
    public void onReservaCancelada(ReservaCanceladaEvent evento) {
        sesionService.cancelarSesionProgramada(evento.getReservaId());
    }
}