package com.tinku.reservas.service;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.PedidoPrevio;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.PedidoPrevioRepository;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.resumen.anonimizacion.AnonimizadorTranscript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Pedido previo a la clase (FR-RES-027): un texto que escribe quien pagó (con un Menor, siempre su
 * Adulto Responsable: el Menor nunca es el pagador, Art. II) y leen él y el Tutor. Filtrado con el
 * mismo anonimizador que M6. Solo texto: sin archivos (decisión del dueño, 2026-09-27).
 */
@Service
public class PedidoPrevioService {

    public static final int MAX_TEXTO = 1000;

    private final ReservaRepository reservaRepo;
    private final PedidoPrevioRepository pedidoRepo;
    private final AnonimizadorTranscript anonimizador;

    public PedidoPrevioService(ReservaRepository reservaRepo, PedidoPrevioRepository pedidoRepo,
                               AnonimizadorTranscript anonimizador) {
        this.reservaRepo = reservaRepo;
        this.pedidoRepo = pedidoRepo;
        this.anonimizador = anonimizador;
    }

    public record Vista(UUID reservaId, String texto, Instant updatedAt, boolean editable) {
    }

    @Transactional
    public Vista guardar(Usuario usuario, UUID reservaId, String texto) {
        Reserva reserva = reservaDelPagador(usuario, reservaId);
        if (!editable(reserva, Instant.now())) {
            throw new PedidoPrevioNoEditableException();
        }
        if (texto == null || texto.isBlank()) {
            throw new PedidoPrevioVacioException();
        }
        String limpio = anonimizador.anonimizar(texto.strip());
        if (limpio.length() > MAX_TEXTO) {
            limpio = limpio.substring(0, MAX_TEXTO);
        }
        PedidoPrevio pedido = pedidoRepo.findById(reservaId).orElseGet(() -> new PedidoPrevio(reservaId));
        pedido.setTexto(limpio);
        pedido.setUpdatedAt(Instant.now());
        return vista(pedidoRepo.save(pedido), reserva);
    }

    @Transactional(readOnly = true)
    public Optional<Vista> ver(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (!esPagador(reserva, usuario) && !esTutor(reserva, usuario)) {
            throw new ReservaNoEncontradaException();
        }
        return pedidoRepo.findById(reservaId).map(p -> vista(p, reserva));
    }

    static boolean editable(Reserva reserva, Instant ahora) {
        return (reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO
                || reserva.getEstado() == EstadoReserva.CONFIRMADA)
                && ahora.isBefore(reserva.getHorario());
    }

    private static Vista vista(PedidoPrevio p, Reserva reserva) {
        return new Vista(p.getReservaId(), p.getTexto(), p.getUpdatedAt(), editable(reserva, Instant.now()));
    }

    private Reserva reservaDelPagador(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (esPagador(reserva, usuario)) {
            return reserva;
        }
        if (esTutor(reserva, usuario) || reserva.getBeneficiario().getId().equals(usuario.getId())) {
            throw new SoloPagadorReservaException();
        }
        throw new ReservaNoEncontradaException();
    }

    private static boolean esPagador(Reserva r, Usuario u) {
        return r.getPagador() != null && r.getPagador().getId().equals(u.getId());
    }

    private static boolean esTutor(Reserva r, Usuario u) {
        return r.getTutor().getId().equals(u.getId());
    }
}
