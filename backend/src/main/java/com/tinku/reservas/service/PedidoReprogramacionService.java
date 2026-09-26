package com.tinku.reservas.service;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.evento.ReservaCanceladaEvent;
import com.tinku.reservas.evento.ReservaReprogramadaEvent;
import com.tinku.reservas.jobs.VencimientoPedidoReprogramacionJob;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.PedidoReprogramacion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.PedidoReprogramacionRepository;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.resumen.anonimizacion.AnonimizadorTranscript;
import com.tinku.shared.notificacion.Notificador;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pedido de reprogramación del Tutor (FR-RES-029..031, D-4 de la enmienda v2.5). El Tutor propone
 * otro horario; quien pagó (con un Menor, su Adulto Responsable: el Menor no decide, Art. II)
 * acepta, o cancela y recibe la devolución completa. Si nadie responde hasta T-60 de la clase
 * original (el mismo límite del cambio de horario, Tabla_Tiempos), vence y la clase se cancela
 * como cancelación del Tutor. Mientras está pendiente, la clase sigue en su horario original.
 */
@Service
public class PedidoReprogramacionService {

    /** Tabla_Tiempos: el pedido se responde (y se puede hacer) hasta T-60 de la clase original. */
    public static final Duration LIMITE = ReservaService.LIMITE_REPROGRAMACION;

    private final ReservaRepository reservaRepo;
    private final PedidoReprogramacionRepository pedidoRepo;
    private final ReservaService reservaService;
    private final AnonimizadorTranscript anonimizador;
    private final Notificador notificador;
    private final Scheduler scheduler;

    public PedidoReprogramacionService(ReservaRepository reservaRepo, PedidoReprogramacionRepository pedidoRepo,
                                       ReservaService reservaService, AnonimizadorTranscript anonimizador,
                                       Notificador notificador, Scheduler scheduler) {
        this.reservaRepo = reservaRepo;
        this.pedidoRepo = pedidoRepo;
        this.reservaService = reservaService;
        this.anonimizador = anonimizador;
        this.notificador = notificador;
        this.scheduler = scheduler;
    }

    public record Vista(UUID id, UUID reservaId, Instant horarioOriginal, Instant horarioPropuesto, String motivo,
                        String estado, Instant createdAt, Instant venceAt, boolean puedoResponder,
                        boolean puedoRetirar) {
    }

    @Transactional
    public Vista pedir(Usuario tutor, UUID reservaId, Instant horarioPropuesto, String motivo) {
        Reserva reserva = reservaRepo.findByIdParaActualizar(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (!esTutor(reserva, tutor)) {
            if (esPagador(reserva, tutor) || reserva.getBeneficiario().getId().equals(tutor.getId())) {
                throw new SoloTutorException("Solo el Tutor de la clase puede proponer otro horario.");
            }
            throw new ReservaNoEncontradaException();
        }
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new ReservaNoReprogramableException();
        }
        Instant ahora = Instant.now();
        if (!ahora.plus(LIMITE).isBefore(reserva.getHorario())) {
            throw new VentanaMinimaException("Falta menos de 1 hora para la clase: ya no se puede proponer "
                    + "otro horario. Si no vas a poder darla, cancelala.");
        }
        if (pedidoRepo.findByReservaIdAndEstado(reservaId, PedidoReprogramacion.PENDIENTE).isPresent()) {
            throw new PedidoReprogramacionException("Ya hay un pedido de cambio de horario esperando respuesta.");
        }
        if (horarioPropuesto == null || horarioPropuesto.equals(reserva.getHorario())) {
            throw new PedidoReprogramacionException("Elegí un horario distinto al actual.");
        }
        reservaService.validarHorarioPropuesto(reserva, horarioPropuesto);
        exigirLibre(reserva, horarioPropuesto);

        PedidoReprogramacion pedido = new PedidoReprogramacion();
        pedido.setReservaId(reservaId);
        pedido.setHorarioOriginal(reserva.getHorario());
        pedido.setHorarioPropuesto(horarioPropuesto);
        pedido.setMotivo(motivo == null || motivo.isBlank() ? null : recortar(anonimizador.anonimizar(motivo.strip())));
        pedidoRepo.save(pedido);
        programarVencimiento(pedido);
        destinatarios(reserva).forEach(id -> notificador.notificar(id, TipoNotificacion.REPROGRAMACION_PEDIDA, Map.of(
                "reservaId", reservaId.toString(),
                "horario", reserva.getHorario().toString(),
                "horarioPropuesto", horarioPropuesto.toString())));
        return vista(pedido, tutor, reserva);
    }

    @Transactional(readOnly = true)
    public Optional<Vista> ver(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (!esTutor(reserva, usuario) && !esPagador(reserva, usuario)) {
            throw new ReservaNoEncontradaException();
        }
        return pedidoRepo.findByReservaIdAndEstado(reservaId, PedidoReprogramacion.PENDIENTE)
                .map(p -> vista(p, usuario, reserva));
    }

    /** FR-RES-030: la clase pasa al horario propuesto (mismo camino que un cambio de horario). */
    @Transactional
    public void aceptar(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaDelPagador(usuario, reservaId);
        PedidoReprogramacion pedido = pendienteVigente(reservaId);
        pedido.resolver(PedidoReprogramacion.ACEPTADO);
        pedidoRepo.save(pedido);
        desagendarVencimiento(pedido.getId());
        reservaService.aplicarNuevoHorario(reserva, pedido.getHorarioPropuesto());
        notificador.notificar(reserva.getTutor().getId(), TipoNotificacion.REPROGRAMACION_ACEPTADA, Map.of(
                "reservaId", reservaId.toString(), "horario", pedido.getHorarioPropuesto().toString()));
    }

    /** FR-RES-030: prefiere cancelar. Cancela el Tutor → devolución total (FR-RES-008). */
    @Transactional
    public void rechazar(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaDelPagador(usuario, reservaId);
        PedidoReprogramacion pedido = pendienteVigente(reservaId);
        pedido.resolver(PedidoReprogramacion.RECHAZADO);
        pedidoRepo.save(pedido);
        desagendarVencimiento(pedido.getId());
        reservaService.cancelarPorElTutor(reserva);
        notificador.notificar(reserva.getTutor().getId(), TipoNotificacion.REPROGRAMACION_RECHAZADA, Map.of(
                "reservaId", reservaId.toString(), "horario", reserva.getHorario().toString(), "motivo", "rechazado"));
    }

    @Transactional
    public void retirar(Usuario tutor, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (!esTutor(reserva, tutor)) {
            throw new ReservaNoEncontradaException();
        }
        PedidoReprogramacion pedido = pedidoRepo.findByReservaIdAndEstado(reservaId, PedidoReprogramacion.PENDIENTE)
                .orElseThrow(() -> new PedidoReprogramacionException("No hay un pedido esperando respuesta."));
        pedido.resolver(PedidoReprogramacion.RETIRADO);
        pedidoRepo.save(pedido);
        desagendarVencimiento(pedido.getId());
    }

    /** FR-RES-031: a T-60 sin respuesta, vence y la clase se cancela con devolución. Idempotente. */
    @Transactional
    public void vencer(UUID pedidoId) {
        PedidoReprogramacion pedido = pedidoRepo.findById(pedidoId).orElse(null);
        if (pedido == null || !PedidoReprogramacion.PENDIENTE.equals(pedido.getEstado())) {
            return;
        }
        pedido.resolver(PedidoReprogramacion.VENCIDO);
        pedidoRepo.save(pedido);
        Reserva reserva = reservaRepo.findById(pedido.getReservaId()).orElse(null);
        if (reserva == null || reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            return;
        }
        reservaService.cancelarPorElTutor(reserva);
        Map<String, String> datos = Map.of("reservaId", reserva.getId().toString(),
                "horario", reserva.getHorario().toString(), "canceladaPor", "tutor");
        destinatarios(reserva).forEach(id -> notificador.notificar(id, TipoNotificacion.CLASE_CANCELADA, datos));
        notificador.notificar(reserva.getTutor().getId(), TipoNotificacion.REPROGRAMACION_RECHAZADA, Map.of(
                "reservaId", reserva.getId().toString(), "horario", reserva.getHorario().toString(), "motivo", "vencido"));
    }

    /** Si la clase se cancela o se mueve por otro camino, el pedido pendiente ya no tiene sentido. */
    @EventListener
    @Transactional
    public void onReservaCancelada(ReservaCanceladaEvent evento) {
        cerrarPendiente(evento.getReservaId());
    }

    @EventListener
    @Transactional
    public void onReservaReprogramada(ReservaReprogramadaEvent evento) {
        cerrarPendiente(evento.getReservaId());
    }

    public TriggerKey triggerVencimiento(UUID pedidoId) {
        return new TriggerKey("vence-pedido-reprog-trigger-" + pedidoId, ReservaService.GRUPO_JOB);
    }

    private void cerrarPendiente(UUID reservaId) {
        pedidoRepo.findByReservaIdAndEstado(reservaId, PedidoReprogramacion.PENDIENTE).ifPresent(p -> {
            p.resolver(PedidoReprogramacion.RETIRADO);
            pedidoRepo.save(p);
            desagendarVencimiento(p.getId());
        });
    }

    private PedidoReprogramacion pendienteVigente(UUID reservaId) {
        PedidoReprogramacion pedido = pedidoRepo.findByReservaIdAndEstado(reservaId, PedidoReprogramacion.PENDIENTE)
                .orElseThrow(() -> new PedidoReprogramacionException("Este pedido ya no está esperando respuesta."));
        if (!Instant.now().isBefore(venceAt(pedido))) {
            throw new PedidoReprogramacionException("Se venció el plazo para responder este pedido.");
        }
        return pedido;
    }

    private static Instant venceAt(PedidoReprogramacion p) {
        return p.getHorarioOriginal().minus(LIMITE);
    }

    /** No choca con otra clase del Tutor ni del alumno (sin contar esta misma). */
    private void exigirLibre(Reserva reserva, Instant inicio) {
        Instant fin = inicio.plus(Duration.ofMinutes(reserva.getDuracionMinutos()));
        List<EstadoReserva> vivas = List.of(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA, EstadoReserva.EN_CURSO);
        Instant desde = inicio.minus(Duration.ofHours(4)); // una clase dura como máximo 3 hs (Tabla_Tiempos)
        boolean ocupado = reservaRepo.findByEstadoInAndHorarioAfterAndTutor_Id(vivas, desde, reserva.getTutor().getId())
                .stream()
                .anyMatch(r -> !r.getId().equals(reserva.getId()) && r.getHorario().isBefore(fin) && inicio.isBefore(r.getHorarioFin()))
                || reservaRepo.findByEstadoInAndHorarioAfterAndBeneficiario_Id(vivas, desde, reserva.getBeneficiario().getId())
                .stream()
                .anyMatch(r -> !r.getId().equals(reserva.getId()) && r.getHorario().isBefore(fin) && inicio.isBefore(r.getHorarioFin()));
        if (ocupado) {
            throw new PedidoReprogramacionException("Ese horario ya está ocupado.");
        }
    }

    private Reserva reservaDelPagador(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findByIdParaActualizar(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (esPagador(reserva, usuario)) {
            return reserva;
        }
        if (esTutor(reserva, usuario) || reserva.getBeneficiario().getId().equals(usuario.getId())) {
            throw new SoloPagadorReservaException();
        }
        throw new ReservaNoEncontradaException();
    }

    /** Quien pagó y, si es otro adulto, el alumno. Nunca el Menor (lo decide su AR). */
    private static Set<UUID> destinatarios(Reserva reserva) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(reserva.getPagador().getId());
        if (reserva.getBeneficiario().getTipo() != com.tinku.identidad.model.TipoUsuario.MENOR) {
            ids.add(reserva.getBeneficiario().getId());
        }
        return ids;
    }

    private Vista vista(PedidoReprogramacion p, Usuario quien, Reserva reserva) {
        boolean vigente = PedidoReprogramacion.PENDIENTE.equals(p.getEstado()) && Instant.now().isBefore(venceAt(p));
        return new Vista(p.getId(), p.getReservaId(), p.getHorarioOriginal(), p.getHorarioPropuesto(), p.getMotivo(),
                p.getEstado(), p.getCreatedAt(), venceAt(p), vigente && esPagador(reserva, quien),
                vigente && esTutor(reserva, quien));
    }

    private static String recortar(String texto) {
        return texto.length() > 300 ? texto.substring(0, 300) : texto;
    }

    private static boolean esPagador(Reserva r, Usuario u) {
        return r.getPagador() != null && r.getPagador().getId().equals(u.getId());
    }

    private static boolean esTutor(Reserva r, Usuario u) {
        return r.getTutor().getId().equals(u.getId());
    }

    private void programarVencimiento(PedidoReprogramacion pedido) {
        JobDetail detail = JobBuilder.newJob(VencimientoPedidoReprogramacionJob.class)
                .withIdentity(new JobKey("vence-pedido-reprog-" + pedido.getId(), ReservaService.GRUPO_JOB))
                .usingJobData(VencimientoPedidoReprogramacionJob.PARAM_PEDIDO_ID, pedido.getId().toString())
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerVencimiento(pedido.getId()))
                .startAt(Date.from(venceAt(pedido)))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
        try {
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            // Fail-closed: un pedido sin su vencimiento dejaría la clase en el aire.
            throw new IllegalStateException("No se pudo agendar el vencimiento del pedido de reprogramación.", e);
        }
    }

    private void desagendarVencimiento(UUID pedidoId) {
        try {
            scheduler.unscheduleJob(triggerVencimiento(pedidoId));
        } catch (SchedulerException e) {
            // Benigno: el job es idempotente (un pedido ya resuelto no hace nada).
        }
    }
}
