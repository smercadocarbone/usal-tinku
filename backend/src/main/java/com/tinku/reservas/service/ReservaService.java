package com.tinku.reservas.service;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.seguridad.evento.DenunciaResueltaEvent;
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
import com.tinku.reservas.port.VerificadorCobroTutor;
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
import com.tinku.shared.notificacion.Notificador;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tinku.reservas.web.ReservaResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * FR-RES-013 — no se reserva a menos de 30 min del inicio (Tabla_Tiempos_Tinku.md; eran 15
     * hasta 2026-09-26): el pago tiene hasta 15 min para confirmarse (FR-RES-020) y el Tutor
     * tiene que enterarse con margen para prepararse.
     */
    public static final Duration VENTANA_MINIMA = Duration.ofMinutes(30);

    /**
     * FR-RES-016 (enmendado 2026-09-26): con menos de 24 hs se puede cambiar de horario, sin
     * perder el pago, hasta el doble de la ventana mínima antes de la clase (1 h). El horario
     * que se libera queda abierto a que otra persona lo reserve durante al menos una ventana
     * mínima más. Sin límite de cambios.
     */
    public static final Duration LIMITE_REPROGRAMACION = VENTANA_MINIMA.multipliedBy(2);

    /** FR-RES-020 — timeout de pendiente_pago a 15 min (Tabla_Tiempos_Tinku.md). */
    public static final Duration TIMEOUT_PENDIENTE_PAGO = Duration.ofMinutes(15);

    /** FR-RES-004/015/016 — reprogramación y cancelación sin penalidad hasta 24hs antes. */

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
    private final PoliticaSesionesMenores politicaMenores;
    private final Notificador notificador;
    private final AdicionalResumen adicionalResumen;
    private final VerificadorCobroTutor verificadorCobro;

    public ReservaService(SolicitudSesionRepository solicitudRepo,
                          UsuarioRepository usuarioRepo,
                          AutorizacionTutorRepository autorizacionRepo,
                          ReservaRepository reservaRepo,
                          FranjaService franjaService,
                          TarifaProveedor tarifaProveedor,
                          ReputacionBloqueoProveedor reputacionBloqueo,
                          ApplicationEventPublisher events,
                          Scheduler scheduler,
                          PoliticaSesionesMenores politicaMenores,
                          Notificador notificador,
                          AdicionalResumen adicionalResumen,
                          VerificadorCobroTutor verificadorCobro) {
        this.solicitudRepo = solicitudRepo;
        this.usuarioRepo = usuarioRepo;
        this.autorizacionRepo = autorizacionRepo;
        this.reservaRepo = reservaRepo;
        this.franjaService = franjaService;
        this.tarifaProveedor = tarifaProveedor;
        this.reputacionBloqueo = reputacionBloqueo;
        this.events = events;
        this.scheduler = scheduler;
        this.politicaMenores = politicaMenores;
        this.notificador = notificador;
        this.adicionalResumen = adicionalResumen;
        this.verificadorCobro = verificadorCobro;
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
                solicitud.getHorarioPropuesto(), solicitud.getDuracionMinutos(), false);

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
            return crearReserva(pagador, pagador, tutor, request.horario(), request.duracionMinutos(),
                    request.conResumen());
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
        return crearReserva(pagador, beneficiario, tutor, request.horario(), request.duracionMinutos(),
                request.conResumen());
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
     * Con menos de 24hs también se puede (FR-RES-016, enmendado 2026-09-26) hasta
     * {@link #LIMITE_REPROGRAMACION} antes de la clase; después se rechaza con un mensaje claro.
     * Nunca cancela: antes, pedir un cambio con menos de 24hs cancelaba la clase sin avisar.
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
        if (Instant.now().plus(LIMITE_REPROGRAMACION).isAfter(reserva.getHorario())) {
            throw new VentanaMinimaException("Falta menos de 1 hora para la clase: ya no se puede "
                    + "cambiar el horario. Si no vas a poder, podés cancelarla.");
        }
        validarNuevoHorario(reserva, nuevoHorario);
        // D6 regla 6: la reprogramación conserva la duración (y el precio) originales.
        reserva.definirHorario(nuevoHorario, reserva.getDuracionMinutos());
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

    /** UX-05 §4: la Reserva como la ve {@code usuario}, con nombres, duración (la
     * guardada en la Reserva, D6) y acciones. Dentro de la transacción:
     * pagador/beneficiario/tutor son asociaciones perezosas. */
    @Transactional(readOnly = true)
    public ReservaResponse vista(Usuario usuario, UUID reservaId) {
        return vista(obtener(usuario, reservaId), usuario, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ReservaResponse> vistas(Usuario usuario) {
        Instant ahora = Instant.now();
        return listarDe(usuario).stream().map(r -> vista(r, usuario, ahora)).toList();
    }

    private ReservaResponse vista(Reserva r, Usuario usuario, Instant ahora) {
        return ReservaResponse.from(r, usuario, ahora);
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
        aCancelar.values().forEach(r ->
                cancelarPorSistema(r, MotivoCancelacion.SANCION, usuarioSancionadoId));
        return aCancelar.size();
    }

    /**
     * PT10 (T02): el Tutor perdió la habilitación para menores. Se cancelan sus reservas
     * futuras cuyo beneficiario es un MENOR, con el Tutor como quien cancela: M5 hace
     * reembolso total (canceló alguien que no es el pagador). Las de adultos siguen.
     * Aviso al Adulto Responsable por cada una.
     */
    @Transactional
    public int cancelarFuturasConMenoresPorCap(UUID tutorId) {
        List<Reserva> conMenores = reservaRepo.findByEstadoInAndHorarioAfterAndTutor_Id(
                        List.of(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA), Instant.now(), tutorId)
                .stream()
                .filter(r -> r.getBeneficiario().getTipo() == TipoUsuario.MENOR)
                .toList();
        conMenores.forEach(r -> {
            cancelarPorSistema(r, MotivoCancelacion.CAP_VENCIDO, tutorId);
            notificador.notificar(r.getPagador().getId(),
                    TipoNotificacion.CLASE_CANCELADA_TUTOR_SIN_HABILITACION,
                    Map.of("reservaId", r.getId().toString(), "horario", r.getHorario().toString()));
        });
        solicitudRepo.findByTutorIdAndEstado(tutorId, EstadoSolicitud.PENDIENTE)
                .forEach(sol -> sol.setEstado(EstadoSolicitud.RECHAZADA));
        return conMenores.size();
    }

    /**
     * FR-ID-014 — baja confirmada de un menor: se cancelan sus reservas futuras
     * (él es siempre el beneficiario). Es un acto del Adulto Responsable, que es el
     * pagador, así que el motivo es {@code voluntaria} y {@code reserva.cancelada}
     * sale con él como quien cancela: M5 aplica FR-RES-008 igual que en su
     * cancelación tardía (con &lt;24hs el Tutor cobra), para que dar de baja al
     * menor no sea la vía para esquivar la penalidad. Misma mecánica que la
     * sanción: {@code pendiente_pago} sin evento, {@code confirmada} con evento.
     * Las Solicitudes pendientes del menor quedan rechazadas (US-3): si no,
     * {@link #aprobarSolicitud} podría convertirlas después en una Reserva paga
     * para un perfil dado de baja.
     */
    @Transactional
    public int cancelarFuturasPorBajaDeMenor(UUID menorId, UUID adultoResponsableId) {
        solicitudRepo.findByMenorIdAndEstado(menorId, EstadoSolicitud.PENDIENTE)
                .forEach(s -> s.setEstado(EstadoSolicitud.RECHAZADA));
        List<Reserva> futuras = reservaRepo.findByEstadoInAndHorarioAfterAndBeneficiario_Id(
                List.of(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA),
                Instant.now(), menorId);
        futuras.forEach(r ->
                cancelarPorSistema(r, MotivoCancelacion.VOLUNTARIA, adultoResponsableId));
        return futuras.size();
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

    private Reserva cancelarPorSistema(Reserva reserva, MotivoCancelacion motivo,
                                       UUID canceladaPorUsuarioId) {
        boolean estabaConfirmada = reserva.getEstado() == EstadoReserva.CONFIRMADA;
        reserva.setEstado(EstadoReserva.CANCELADA);
        reserva.setMotivoCancelacion(motivo);
        reservaRepo.save(reserva);
        cancelarTimeoutPago(reserva.getId());
        if (estabaConfirmada) {
            events.publishEvent(new ReservaCanceladaEvent(
                    this, reserva.getId(), canceladaPorUsuarioId));
        }
        return reserva;
    }

    /** Clave del trigger del timeout de pago de una Reserva (usada en tests). */
    public TriggerKey triggerTimeoutPago(UUID reservaId) {
        return new TriggerKey("timeout-pago-trigger-" + reservaId, GRUPO_JOB);
    }

    private Reserva crearReserva(Usuario pagador, Usuario beneficiario, Usuario tutor,
                                 Instant horario, Integer duracionMinutos, boolean conResumen) {
        exigirTutorReservable(tutor, pagador, beneficiario);
        // ADR-M5-02: con OAuth activo, un Tutor sin MercadoPago conectado no puede cobrar.
        if (!verificadorCobro.puedeCobrar(tutor.getId())) {
            throw new TutorSinCobroException();
        }
        // T-TES-10/DT7: piloto sin menores — cubre la directa (crearDirecta) y la
        // aprobación (aprobarSolicitud), ambas caen acá. Fail-closed (AGENTS §3).
        if (beneficiario.getTipo() == TipoUsuario.MENOR) {
            politicaMenores.validarClaseConMenor(tutor.getId()); // T-TES-10 + FR-ID-026 (CAP)
        }
        // FR-REP-006 (T-M4-10): Tutor con calificación pendiente no toma reservas nuevas.
        if (reputacionBloqueo.tutoresConCalificacionPendiente().contains(tutor.getId())) {
            throw new TutorPendienteCalificacionException();
        }
        if (Instant.now().plus(VENTANA_MINIMA).isAfter(horario)) {
            throw new VentanaMinimaException(
                    "Faltan menos de 30 minutos para el horario — no se puede reservar (FR-RES-013).");
        }
        if (!FranjaService.duracionValida(duracionMinutos)) {
            throw new DuracionMinutosInvalidaException(
                    "La duración tiene que ser de 30 a 180 minutos, en bloques de 30.");
        }
        if (franjaService.franjaQueContiene(tutor.getId(), horario, duracionMinutos).isEmpty()) {
            throw new HorarioFueraDeFranjaException(
                    "El horario no entra entero en una franja del tutor o no empieza en un bloque de 30 minutos.");
        }

        // T09: el adicional de resumen se valida en el backend (Art. II: nunca con un menor).
        if (conResumen) {
            adicionalResumen.validarContratacion(pagador, beneficiario, tutor);
        }

        // FR-PAG-013: el precio se congela al crear la Reserva (fuente: M5, tarifa del Tutor).
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(beneficiario);
        reserva.setTutor(tutor);
        reserva.definirHorario(horario, duracionMinutos);
        // D6 regla 5: precio = tarifa por hora × minutos / 60.
        reserva.setPrecio(tarifaProveedor.precioHora(tutor.getId())
                .multiply(BigDecimal.valueOf(duracionMinutos))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        if (conResumen) {
            reserva.setResumenContratado(true);
            reserva.setPrecioAdicionalResumen(adicionalResumen.precio()); // congelado como el precio
        }
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

    /**
     * Revisión por rol, punto 1 (una sola regla para directa, aprobación y reprogramación):
     * el tutor tiene que ser un TUTOR con la cuenta ACTIVA (si no, 404: no se filtra su estado),
     * distinto del pagador y del beneficiario, y nunca el Adulto Responsable del menor (422).
     */
    static void exigirTutorReservable(Usuario tutor, Usuario pagador, Usuario beneficiario) {
        if (tutor.getTipo() != TipoUsuario.TUTOR || tutor.getEstadoCuenta() != EstadoCuenta.ACTIVA) {
            throw new TutorNoEncontradoException();
        }
        if (tutor.getId().equals(pagador.getId()) || tutor.getId().equals(beneficiario.getId())) {
            throw new AutoReservaNoPermitidaException("No podés reservar una clase con vos mismo.");
        }
        if (beneficiario.getAdultoResponsable() != null
                && tutor.getId().equals(beneficiario.getAdultoResponsable().getId())) {
            throw new AutoReservaNoPermitidaException(
                    "Un tutor no puede dar clases a un menor del que es el adulto responsable.");
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
        exigirTutorReservable(reserva.getTutor(), reserva.getPagador(), reserva.getBeneficiario());
        if (Instant.now().plus(VENTANA_MINIMA).isAfter(nuevoHorario)) {
            throw new VentanaMinimaException(
                    "Faltan menos de 30 minutos para el nuevo horario — no se puede reprogramar (FR-RES-013).");
        }
        if (franjaService.franjaQueContiene(reserva.getTutor().getId(), nuevoHorario,
                reserva.getDuracionMinutos()).isEmpty()) {
            throw new HorarioFueraDeFranjaException(
                    "El nuevo horario no entra entero en una franja del tutor o no empieza en un bloque de 30 minutos.");
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
