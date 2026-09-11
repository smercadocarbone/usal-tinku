package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.evento.DenunciaResueltaEvent;
import com.tinku.reservas.evento.ReservaCanceladaEvent;
import com.tinku.reservas.evento.ReservaConfirmadaEvent;
import com.tinku.reservas.evento.ReservaReprogramadaEvent;
import com.tinku.reservas.jobs.ReservaTimeoutPagoJob;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.model.SolicitudSesion;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import com.tinku.reservas.port.TarifaProveedor;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.repository.SolicitudSesionRepository;
import com.tinku.reservas.web.NuevaReservaDirectaRequest;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creación de Reservas. Dos vías (Spec M4, US-3/US-4):
 * <ul>
 *   <li>por aprobación de una Solicitud del menor (US-3/US-4, T-M4-04);</li>
 *   <li>directa, sin Solicitud intermedia (FR-RES-001, T-M4-05) — Estudiante
 *       adulto para sí mismo o Adulto Responsable por un menor a su cargo.</li>
 * </ul>
 * En ambas la Reserva la crea SOLO quien puede pagar (Artículo II) y queda en
 * {@code pendiente_pago} con un timeout de 15 min (FR-RES-020, T-M4-06) que se
 * cancela explícitamente al confirmarse el pago.
 */
@Service
public class ReservaService {

    /** FR-RES-013 — no se reserva a menos de 15 min del inicio (Tabla_Tiempos_Tinku.md). */
    private static final Duration VENTANA_MINIMA = Duration.ofMinutes(15);

    /** FR-RES-020 — timeout de pendiente_pago a 15 min (Tabla_Tiempos_Tinku.md). */
    private static final Duration TIMEOUT_PENDIENTE_PAGO = Duration.ofMinutes(15);

    /** FR-RES-004/015/016 — reprogramación y cancelación sin penalidad hasta 24hs antes. */
    private static final Duration VENTANA_CANCELACION = Duration.ofHours(24);

    /** Mismo grupo que el job de expiración de Solicitudes (todos los jobs de M4). */
    public static final String GRUPO_JOB = "m4-reservas";

    private final SolicitudSesionRepository solicitudRepo;
    private final UsuarioRepository usuarioRepo;
    private final AutorizacionTutorRepository autorizacionRepo;
    private final ReservaRepository reservaRepo;
    private final FranjaService franjaService;
    private final TarifaProveedor tarifaProveedor;
    private final ReputacionBloqueoProveedor reputacionBloqueo;
    private final ApplicationEventPublisher events;
    private final Scheduler scheduler;

    public ReservaService(SolicitudSesionRepository solicitudRepo,
                          UsuarioRepository usuarioRepo,
                          AutorizacionTutorRepository autorizacionRepo,
                          ReservaRepository reservaRepo,
                          FranjaService franjaService,
                          TarifaProveedor tarifaProveedor,
                          ReputacionBloqueoProveedor reputacionBloqueo,
                          ApplicationEventPublisher events,
                          Scheduler scheduler) {
        this.solicitudRepo = solicitudRepo;
        this.usuarioRepo = usuarioRepo;
        this.autorizacionRepo = autorizacionRepo;
        this.reservaRepo = reservaRepo;
        this.franjaService = franjaService;
        this.tarifaProveedor = tarifaProveedor;
        this.reputacionBloqueo = reputacionBloqueo;
        this.events = events;
        this.scheduler = scheduler;
    }

    /**
     * Convierte la Solicitud pendiente en Reserva con estado pendiente_pago.
     * El pago en sí lo dispara M5 (dependencia pendiente); esta transacción
     * garantiza la fila en `reservas` y el avance de la Solicitud a convertida.
     */
    @Transactional
    public Reserva aprobarSolicitud(Usuario adultoResponsable, UUID solicitudId) {
        exigirCapacidadAdultoResponsable(adultoResponsable);

        SolicitudSesion solicitud = solicitudRepo.findByIdAndEstado(
                solicitudId, EstadoSolicitud.PENDIENTE)
                .orElseThrow(SolicitudNoPendienteException::new);

        // FR-ID-020: solo puede operar menores a su cargo.
        Usuario menor = solicitud.getMenor();
        if (menor.getAdultoResponsable() == null
                || !menor.getAdultoResponsable().getId().equals(adultoResponsable.getId())) {
            throw new SolicitudMenorNoPerteneceException();
        }

        Reserva reserva = crearReserva(adultoResponsable, menor, solicitud.getTutor(),
                solicitud.getHorarioPropuesto());

        solicitud.setEstado(EstadoSolicitud.CONVERTIDA);
        solicitudRepo.save(solicitud);
        return reserva;
    }

    /**
     * Reserva directa sin Solicitud intermedia (FR-RES-001, T-M4-05). Si no se
     * manda {@code beneficiarioId} es un Estudiante adulto reservando para sí
     * mismo; si se manda, el pagador tiene que ser el Adulto Responsable de ese
     * menor y el Tutor debe estar autorizado para él (FR-RES-021, mismo criterio
     * que la Solicitud). La rama la decide el servicio con datos propios de M1 —
     * nunca por un flag del request.
     */
    @Transactional
    public Reserva crearDirecta(Usuario pagador, NuevaReservaDirectaRequest request) {
        Usuario tutor = usuarioRepo.findById(request.tutorId())
                .orElseThrow(TutorNoEncontradoException::new);

        if (request.beneficiarioId() == null) {
            exigirCapacidadEstudiante(pagador);
            return crearReserva(pagador, pagador, tutor, request.horario());
        }

        exigirCapacidadAdultoResponsable(pagador);
        Usuario beneficiario = usuarioRepo.findById(request.beneficiarioId())
                .orElseThrow(BeneficiarioNoPerteneceException::new);
        if (beneficiario.getTipo() != TipoUsuario.MENOR
                || beneficiario.getAdultoResponsable() == null
                || !beneficiario.getAdultoResponsable().getId().equals(pagador.getId())) {
            throw new BeneficiarioNoPerteneceException();
        }
        if (!autorizacionRepo.findTutorIdsByAdultoResponsableIdAndMenorIdAndNoConfiableFalse(
                pagador.getId(), beneficiario.getId()).contains(request.tutorId())) {
            throw new TutorNoAutorizadoParaMenorException();
        }
        return crearReserva(pagador, beneficiario, tutor, request.horario());
    }

    /**
     * STUB TEMPORAL — reemplazar cuando M5 implemente el webhook real de
     * MercadoPago (Chunk M5-B). Simula la confirmación del pago: transición
     * pendiente_pago → confirmada, cancelando el timeout de pago (T-M4-06).
     * Idempotente (los webhooks de MP se reintentan, así que confirmar algo ya
     * confirmado no es un error). Al confirmar, emite {@link ReservaConfirmadaEvent}
     * para que M3 cree y agende la Sesión de Aprendizaje (T-M3-03/04/05).
     */
    @Transactional
    public Reserva confirmarPagoSimulado(UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO) {
            reserva.setEstado(EstadoReserva.CONFIRMADA);
            reservaRepo.save(reserva);
            cancelarTimeoutPago(reserva.getId());
            events.publishEvent(new ReservaConfirmadaEvent(this, reserva.getId()));
        }
        return reserva;
    }

    /**
     * Reprogramación de una Reserva confirmada (T-M4-07, US-5, FR-RES-015/016).
     * Con ≥24hs al horario actual: se actualiza {@code horario} en la MISMA fila,
     * sin tocar el precio original (FR-PAG-013) y sin transacción nueva; emite
     * {@code reserva.reprogramada} para que M3 re-agende la Sesión derivada.
     * Con <24hs se trata como cancelación tardía por quien pagó (FR-RES-016):
     * la Reserva queda cancelada y se emite {@code reserva.cancelada}.
     */
    @Transactional
    public Reserva reprogramar(Usuario usuario, UUID reservaId, Instant nuevoHorario) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new ReservaNoReprogramableException();
        }
        if (!esPagador(reserva, usuario)) {
            throw new SoloPagadorReservaException();
        }
        if (Instant.now().plus(VENTANA_CANCELACION).isAfter(reserva.getHorario())) {
            // FR-RES-016: menos de 24hs → cancelación tardía (asimetría de US-7).
            return cancelarInterna(reserva, usuario);
        }
        validarNuevoHorario(reserva, nuevoHorario);
        reserva.setHorario(nuevoHorario);
        reservaRepo.save(reserva);
        events.publishEvent(new ReservaReprogramadaEvent(this, reserva.getId()));
        return reserva;
    }

    /**
     * Cancelación manual (T-M4-08, US-6/US-7, FR-RES-004/008/017). La puede
     * disparar quien pagó o el Tutor. Si la Reserva está {@code pendiente_pago},
     * no hay nada que cobrar/reembolsar (FR-RES-017): se cancela sin evento y se
     * desprograma el timeout. Si está {@code confirmada}, se emite {@code
     * reserva.cancelada} con quién canceló — la asimetría del escrow la decide M5
     * (Plan M4 §2.5) y M3 desagenda la Sesión derivada.
     */
    @Transactional
    public Reserva cancelar(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO
                && reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new ReservaNoCancelableException();
        }
        if (!esPagador(reserva, usuario) && !esTutor(reserva, usuario)) {
            throw new NoPuedeCancelarReservaException();
        }
        return cancelarInterna(reserva, usuario);
    }

    private Reserva cancelarInterna(Reserva reserva, Usuario cancelante) {
        boolean estabaConfirmada = reserva.getEstado() == EstadoReserva.CONFIRMADA;
        reserva.setEstado(EstadoReserva.CANCELADA);
        reserva.setMotivoCancelacion(MotivoCancelacion.VOLUNTARIA);
        reservaRepo.save(reserva);
        cancelarTimeoutPago(reserva.getId());
        if (estabaConfirmada) {
            // Manual con escrow: M5 decide reembolso/liberación; M3 limpia su Sesión.
            events.publishEvent(new ReservaCanceladaEvent(
                    this, reserva.getId(), cancelante.getId()));
        }
        return reserva;
    }

    /** Reglas comunes a toda creación de Reserva y al timeout (FR-RES-020). Si la
     * Reserva ya dejó de estar {@code pendiente_pago}, no hace nada (idempotente). */
    @Transactional
    public void expirarPorTimeoutPago(UUID reservaId) {
        reservaRepo.findById(reservaId).ifPresent(this::expirarSiSiguePendiente);
    }

    /** GET /api/reservas — reservas donde el usuario es pagador, beneficiario o tutor. */
    @Transactional(readOnly = true)
    public List<Reserva> listarDe(Usuario usuario) {
        return reservaRepo.findByPagador_IdOrBeneficiario_IdOrTutor_IdOrderByHorario(
                usuario.getId(), usuario.getId(), usuario.getId());
    }

    /** GET /api/reservas/{id} — detalle. Solo participantes; caso contrario 404
     * (no se filtra si la reserva existe). */
    @Transactional(readOnly = true)
    public Reserva obtener(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(ReservaNoEncontradaException::new);
        if (!esParticipante(reserva, usuario)) {
            throw new ReservaNoEncontradaException();
        }
        return reserva;
    }

    /** Barrido de recuperación (FR-RES-020): expira reservas pendientes cuyo
     * timeout ya venció — cubre el caso de un job perdido (misma mecánica que
     * {@code SolicitudService.expirarVencidas}). */
    @Transactional
    public int expirarPendientesDePago() {
        List<Reserva> vencidas = reservaRepo.findByEstadoAndCreatedAtBefore(
                EstadoReserva.PENDIENTE_PAGO, Instant.now().minus(TIMEOUT_PENDIENTE_PAGO));
        vencidas.forEach(this::expirarSiSiguePendiente);
        return vencidas.size();
    }

    /**
     * T-M4-09 — sanción de M9 (FR-SEC-008/012): se cancelan las reservas
     * futuras del sancionado con motivo {@code sancion}. Cobertura doble: las
     * que él (sancionado) dicta como Tutor y las que paga como Adulto
     * Responsable/Estudiante — un usuario puede estar en ambos roles en
     * reservas distintas. Las {@code pendiente_pago} se cancelan sin evento
     * (mismo criterio que FR-RES-017: no hay dinero en juego); las
     * {@code confirmada} emiten {@code reserva.cancelada} para que M5 resuelva
     * el reembolso con el contexto de la denuncia (FR-PAG-011/FR-SEC-012) y M3
     * desagende su Sesión. Idempotente (solo mira estados cancelables).
     */
    @Transactional
    public int cancelarFuturasPorSancion(UUID usuarioSancionadoId) {
        List<EstadoReserva> cancelables = List.of(
                EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA);
        Instant ahora = Instant.now();
        Map<UUID, Reserva> aCancelar = new LinkedHashMap<>();
        reservaRepo.findByEstadoInAndHorarioAfterAndTutor_Id(cancelables, ahora, usuarioSancionadoId)
                .forEach(r -> aCancelar.putIfAbsent(r.getId(), r));
        reservaRepo.findByEstadoInAndHorarioAfterAndPagador_Id(cancelables, ahora, usuarioSancionadoId)
                .forEach(r -> aCancelar.putIfAbsent(r.getId(), r));
        aCancelar.values().forEach(r -> cancelarPorSancion(r, usuarioSancionadoId));
        return aCancelar.size();
    }

    /** {@code denuncia.resuelta} ← M9 (FR-SEC-008/012, Chunk M4-E): listener del
     * evento enriquecido por M9-D. Corre en la transacción del publicador; si
     * falla, se aborta (fail-closed). Guard de FR-SEC-011: una resolución
     * {@code infundada} NO cancela reservas futuras (no hay sanción) — solo las
     * fundada/escalada lo hacen. Un evento del stub original (resolución null)
     * conserva el comportamiento de cancelar. */
    @EventListener
    @Transactional
    public void onDenunciaResuelta(DenunciaResueltaEvent evento) {
        if (evento.getResolucion() == null
                || evento.getResolucion() != com.tinku.shared.ResolucionDenuncia.INFUNDADA) {
            cancelarFuturasPorSancion(evento.getUsuarioSancionadoId());
        }
    }

    private Reserva cancelarPorSancion(Reserva reserva, UUID usuarioSancionadoId) {
        boolean estabaConfirmada = reserva.getEstado() == EstadoReserva.CONFIRMADA;
        reserva.setEstado(EstadoReserva.CANCELADA);
        reserva.setMotivoCancelacion(MotivoCancelacion.SANCION);
        reservaRepo.save(reserva);
        cancelarTimeoutPago(reserva.getId());
        if (estabaConfirmada) {
            events.publishEvent(new ReservaCanceladaEvent(
                    this, reserva.getId(), usuarioSancionadoId));
        }
        return reserva;
    }

    /** Clave del trigger del timeout de pago de una Reserva (usada en tests). */
    public TriggerKey triggerTimeoutPago(UUID reservaId) {
        return new TriggerKey("timeout-pago-trigger-" + reservaId, GRUPO_JOB);
    }

    private Reserva crearReserva(Usuario pagador, Usuario beneficiario, Usuario tutor, Instant horario) {
        // FR-REP-006 (T-M4-10): Tutor con calificación pendiente no toma reservas nuevas.
        if (reputacionBloqueo.tutoresConCalificacionPendiente().contains(tutor.getId())) {
            throw new TutorPendienteCalificacionException();
        }
        if (Instant.now().plus(VENTANA_MINIMA).isAfter(horario)) {
            throw new VentanaMinimaException(
                    "Faltan menos de 15 minutos para el horario — no se puede reservar (FR-RES-013).");
        }
        if (!franjaService.estaDentroDeFranjaActiva(tutor.getId(), horario)) {
            throw new HorarioFueraDeFranjaException(
                    "La franja ya no está activa o ya no cubre el horario (FR-RES-012).");
        }

        // FR-PAG-013: el precio se congela al crear la Reserva (fuente: M5, tarifa del Tutor).
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(beneficiario);
        reserva.setTutor(tutor);
        reserva.setHorario(horario);
        reserva.setPrecio(tarifaProveedor.tarifaPorSesion(tutor.getId()));
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        Reserva guardada = reservaRepo.save(reserva);
        programarTimeoutPago(guardada);
        return guardada;
    }

    /** Job puntual de Quartz a {@code created_at + 15min} (FR-RES-020, persistido). */
    void programarTimeoutPago(Reserva reserva) {
        JobDetail detail = JobBuilder.newJob(ReservaTimeoutPagoJob.class)
                .withIdentity("timeout-pago-" + reserva.getId(), GRUPO_JOB)
                .usingJobData(ReservaTimeoutPagoJob.PARAM_RESERVA_ID, reserva.getId().toString())
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerTimeoutPago(reserva.getId()).getName(), GRUPO_JOB)
                .startAt(Date.from(Instant.now().plus(TIMEOUT_PENDIENTE_PAGO)))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionIgnoreMisfires())
                .build();
        try {
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            // Fail-closed: una reserva pendiente de pago sin su deadline no debe
            // nacer aunque el scheduler falle.
            throw new IllegalStateException("No se pudo programar el timeout de pago de la Reserva.", e);
        }
    }

    /** Cancelación explícita al confirmarse el pago (mismo patrón que M3-B con el no-show). */
    void cancelarTimeoutPago(UUID reservaId) {
        try {
            scheduler.unscheduleJob(triggerTimeoutPago(reservaId));
        } catch (SchedulerException e) {
            // Benigno: si el job ya corrió o no se pudo remover, la idempotencia
            // de expirarPorTimeoutPago protege (estado != pendiente_pago → no-op).
        }
    }

    private void expirarSiSiguePendiente(Reserva reserva) {
        if (reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO) {
            reserva.setEstado(EstadoReserva.CANCELADA);
            reserva.setMotivoCancelacion(MotivoCancelacion.TIMEOUT_PAGO);
            reservaRepo.save(reserva);
        }
    }

    private void exigirCapacidadAdultoResponsable(Usuario usuario) {
        if (usuario.getTipo() == TipoUsuario.MENOR || !usuario.isCapacidadAdultoResponsable()) {
            throw new SoloAdultoResponsableException(
                    "Solo la capacidad 'Adulto Responsable' puede aprobar y crear Reservas.");
        }
    }

    private void exigirCapacidadEstudiante(Usuario usuario) {
        if (usuario.getTipo() == TipoUsuario.MENOR || !usuario.isCapacidadEstudiante()) {
            throw new CapacidadDePagoRequeridaException();
        }
    }

    private void validarNuevoHorario(Reserva reserva, Instant nuevoHorario) {
        if (Instant.now().plus(VENTANA_MINIMA).isAfter(nuevoHorario)) {
            throw new VentanaMinimaException(
                    "Faltan menos de 15 minutos para el nuevo horario — no se puede reprogramar (FR-RES-013).");
        }
        if (!franjaService.estaDentroDeFranjaActiva(reserva.getTutor().getId(), nuevoHorario)) {
            throw new HorarioFueraDeFranjaException(
                    "El nuevo horario no cae en una franja publicada y activa (FR-RES-012).");
        }
    }

    private boolean esPagador(Reserva reserva, Usuario usuario) {
        return reserva.getPagador() != null
                && reserva.getPagador().getId().equals(usuario.getId());
    }

    private boolean esTutor(Reserva reserva, Usuario usuario) {
        return reserva.getTutor() != null
                && reserva.getTutor().getId().equals(usuario.getId());
    }

    private boolean esParticipante(Reserva reserva, Usuario usuario) {
        return esPagador(reserva, usuario)
                || esTutor(reserva, usuario)
                || (reserva.getBeneficiario() != null
                && reserva.getBeneficiario().getId().equals(usuario.getId()));
    }
}
