package com.tinku.admin.service;

import com.tinku.identidad.model.Usuario;
import com.tinku.pagos.model.Transaccion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test de {@link AlertaSoporteProveedorTicket}: el ticket de Soporte
 * Financiero nace una sola vez, cuando la liberación AGOTA sus reintentos
 * (FR-PAG-007 / T-M8-05), a nombre del Tutor de la Reserva; y nunca lanza (la
 * alerta corre dentro del flujo de liberación y no puede tumbarlo).
 */
class AlertaSoporteProveedorTicketTest {

    private TicketSoporteService ticketService;
    private ReservaRepository reservaRepo;
    private AlertaSoporteProveedorTicket alerta;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketSoporteService.class);
        reservaRepo = mock(ReservaRepository.class);
        alerta = new AlertaSoporteProveedorTicket(ticketService, reservaRepo);
    }

    private Transaccion transaccion(int intentos) {
        Transaccion t = new Transaccion();
        t.setId(UUID.randomUUID());
        t.setReservaId(UUID.randomUUID());
        t.setIntentosLiberacion(intentos);
        return t;
    }

    private Reserva reservaConTutor(Usuario tutor) {
        Reserva r = new Reserva();
        r.setTutor(tutor);
        return r;
    }

    @Test
    void agotaReintentos_abreUnTicketParaElTutor() {
        Usuario tutor = new Usuario();
        tutor.setId(UUID.randomUUID());
        Transaccion t = transaccion(AlertaSoporteProveedorTicket.INTENTOS_AGOTADOS);
        when(reservaRepo.findById(t.getReservaId()))
                .thenReturn(Optional.of(reservaConTutor(tutor)));
        // El fallo posterior del reintento manual llega con intentos=4: no re-abre.
        Transaccion tPosterior = transaccion(AlertaSoporteProveedorTicket.INTENTOS_AGOTADOS + 1);
        when(reservaRepo.findById(tPosterior.getReservaId()))
                .thenReturn(Optional.of(reservaConTutor(tutor)));

        alerta.notificarFalloLiberacion(t);
        alerta.notificarFalloLiberacion(tPosterior);

        verify(ticketService).crear(eq(tutor), eq("M5.pago_fallido"), any(), any());
    }

    @Test
    void reintentosEnCurso_noAbrenTicket() {
        Transaccion t = transaccion(1);
        alerta.notificarFalloLiberacion(t);
        verifyNoInteractions(ticketService);
    }

    @Test
    void sinReserva_noAbreTicket() {
        Transaccion t = transaccion(AlertaSoporteProveedorTicket.INTENTOS_AGOTADOS);
        when(reservaRepo.findById(t.getReservaId())).thenReturn(Optional.empty());
        alerta.notificarFalloLiberacion(t);
        verifyNoInteractions(ticketService);
    }

    @Test
    void siElTicketFalla_nuncaLanza() {
        Usuario tutor = new Usuario();
        tutor.setId(UUID.randomUUID());
        Transaccion t = transaccion(AlertaSoporteProveedorTicket.INTENTOS_AGOTADOS);
        when(reservaRepo.findById(t.getReservaId()))
                .thenReturn(Optional.of(reservaConTutor(tutor)));
        when(ticketService.crear(eq(tutor), any(), any(), any()))
                .thenThrow(new IllegalStateException("mapeo origen no existe"));

        assertDoesNotThrow(() -> alerta.notificarFalloLiberacion(t));
    }
}