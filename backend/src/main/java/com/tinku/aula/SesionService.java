package com.tinku.aula;

import com.tinku.pagos.evento.SesionEvento;
import com.tinku.pagos.evento.SesionFinalizadaEvent;
import com.tinku.pagos.evento.SesionInterrumpidaEvent;
import com.tinku.pagos.evento.SesionKillswitchAdultosEvent;
import com.tinku.pagos.evento.SesionKillswitchMenorEvent;
import com.tinku.pagos.evento.SesionNoShowDobleEvent;
import com.tinku.pagos.evento.SesionNoShowEstudianteEvent;
import com.tinku.pagos.evento.SesionNoShowTutorEvent;
import com.tinku.aula.jobs.CorteAutomaticoJob;
import com.tinku.aula.jobs.CrearSalaJob;
import com.tinku.aula.jobs.NoShowJob;
import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.aula.model.ConfirmacionKillswitch;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.ConfirmacionKillswitchRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.FranjaService;
import com.tinku.reservas.service.ReservaNoEncontradaException;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Ciclo de vida de la Sesión de Aprendizaje (T-M3-03/04/05). Nace al confirmarse
 * la Reserva (evento M4→M3), y desde ahí los 3 timeouts NO viven en memoria:
 * se agendan en el JobStore de Quartz persistido (Artículo IV/X, igual patrón que
 * SolicitudService.programarExpiracion):
 *
 *  1. {@code horario - 5min}  → {@link CrearSalaJob}  — creación diferida de sala (US-1).
 *  2. {@code horario + 10min} → {@link NoShowJob}     — no-show (US-7), se CANCELA
 *     explícitamente al recibir el segundo join (Plan M3 §3.2 punto 4).
 *  3. {@code horario_fin + 5min} → {@link CorteAutomaticoJob} — fin automático (US-8).
 *
 * Todos los cierres de sesión (finalizar, corte, no-show) son idempotentes y se
 * guardan por los guards de estado: quien llegue segundo no re-emite el evento.
 */
@Service
public class SesionService {

    /** Grupo de jobs de sesión en el JOB_STORE (mismo estilo que m4-reservas). */
    public static final String GRUPO_JOB = "m3-aula";

    /** T-5: la sala se crea 5 minutos antes del arranque (Plan M3 §3.1, Tabla_Tiempos). */
    private static final Duration ANTICIPACION_CREACION_SALA = Duration.ofMinutes(5);
    /** T+10: el no-show se decide a los 10 minutos de arranque (Plan M3 §3.2, Tabla_Tiempos). */
    private static final Duration TIMEOUT_NO_SHOW = Duration.ofMinutes(10);
    /** +5 min tras el fin agendado para el corte automático (Plan M3 §3.5, Tabla_Tiempos). */
    private static final Duration TOLERANCIA_FIN_AUTOMATICO = Duration.ofMinutes(5);

    private final SesionAprendizajeRepository sesionRepo;
    private final ReservaRepository reservaRepo;
    private final UsuarioRepository usuarioRepo;
    private final AlertaSeguridadRepository alertaRepo;
    private final ConfirmacionKillswitchRepository confirmacionRepo;
    private final FranjaService franjaService;
    private final LiveKitService liveKitService;
    private final Scheduler scheduler;
    private final ApplicationEventPublisher events;

    public SesionService(SesionAprendizajeRepository sesionRepo,
                         ReservaRepository reservaRepo,
                         UsuarioRepository usuarioRepo,
                         AlertaSeguridadRepository alertaRepo,
                         ConfirmacionKillswitchRepository confirmacionRepo,
                         FranjaService franjaService,
                         LiveKitService liveKitService,
                         Scheduler scheduler,
                         ApplicationEventPublisher events) {
        this.sesionRepo = sesionRepo;
        this.reservaRepo = reservaRepo;
        this.usuarioRepo = usuarioRepo;
        this.alertaRepo = alertaRepo;
        this.confirmacionRepo = confirmacionRepo;
        this.franjaService = franjaService;
        this.liveKitService = liveKitService;
        this.scheduler = scheduler;
        this.events = events;
    }

    // ------------------------------------------------ creación y agenda (T-M3-03)

    /**
     * Crea la Sesión 1:1 con la Reserva confirmada y agenda sus 3 jobs de Quartz.
     * Idempotente por sesión y por trigger: re-ejecutar (o una reserva ya agendada)
     * no duplica filas ni jobs.
     */
    @Transactional
    public SesionAprendizaje programarSesion(UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new IllegalStateException(
                    "Solo se agenda la Sesión de una Reserva confirmada (T-M3-03).");
        }
        SesionAprendizaje sesion = sesionRepo.findByReservaId(reservaId).orElseGet(() -> {
            SesionAprendizaje s = new SesionAprendizaje();
            s.setReservaId(reservaId);
            s.setEstado(SesionAprendizaje.ESTADO_NO_INICIADA);
            return sesionRepo.save(s);
        });

        Duration duracionFranja = franjaService.duracionFranjaQueCubre(
                reserva.getTutor().getId(), reserva.getHorario())
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró la franja que cubre el horario de la Reserva: no se "
                                + "pueden agendar los jobs de la sesión (T-M3-03)."));
        programarSiFalta(sesion.getId(), CrearSalaJob.class,
                reserva.getHorario().minus(ANTICIPACION_CREACION_SALA));
        programarSiFalta(sesion.getId(), NoShowJob.class,
                reserva.getHorario().plus(TIMEOUT_NO_SHOW));
        programarSiFalta(sesion.getId(), CorteAutomaticoJob.class,
                reserva.getHorario().plus(duracionFranja).plus(TOLERANCIA_FIN_AUTOMATICO));
        sesion.setDuracionAgendadaSegundos((int) duracionFranja.getSeconds());
        return sesion;
    }

    /**
     * Re-agenda los 3 jobs de la Sesión al NUEVO horario de su Reserva
     * ({@code reserva.reprogramada}, T-M4-07 → M3). Si la Reserva se reprogramó
     * pero todavía no tenía Sesión (no debería pasar: nace al confirmar), no hace
     * nada. Desagenda los triggers viejos y los vuelve a agendar, recalculando la
     * duración de la franja que cubre el nuevo horario.
     */
    @Transactional
    public void reprogramarSesionProgramada(UUID reservaId) {
        sesionRepo.findByReservaId(reservaId).ifPresent(sesion -> {
            Reserva reserva = reservaRepo.findById(reservaId).orElse(null);
            if (reserva == null) {
                return;
            }
            Duration duracionFranja = franjaService.duracionFranjaQueCubre(
                    reserva.getTutor().getId(), reserva.getHorario())
                    .orElseThrow(() -> new IllegalStateException(
                            "No se encontró la franja del NUEVO horario de la Reserva "
                                    + reservaId + ": no se pueden re-agendar los jobs (T-M4-07)."));
            desagendar(sesion.getId());
            programarSiFalta(sesion.getId(), CrearSalaJob.class,
                    reserva.getHorario().minus(ANTICIPACION_CREACION_SALA));
            programarSiFalta(sesion.getId(), NoShowJob.class,
                    reserva.getHorario().plus(TIMEOUT_NO_SHOW));
            programarSiFalta(sesion.getId(), CorteAutomaticoJob.class,
                    reserva.getHorario().plus(duracionFranja).plus(TOLERANCIA_FIN_AUTOMATICO));
            sesion.setDuracionAgendadaSegundos((int) duracionFranja.getSeconds());
            sesionRepo.save(sesion);
        });
    }

    /**
     * Desagenda los 3 jobs de la Sesión ({@code reserva.cancelada}, T-M4-08 → M3).
     * Limpieza: los jobs ya serían no-op por el guard de estado, pero no dejamos
     * disparos muertos.
     */
    @Transactional
    public void cancelarSesionProgramada(UUID reservaId) {
        sesionRepo.findByReservaId(reservaId).ifPresent(sesion -> desagendar(sesion.getId()));
    }

    private void desagendar(UUID sesionId) {
        try {
            scheduler.unscheduleJob(triggerSala(sesionId));
            scheduler.unscheduleJob(triggerNoShow(sesionId));
            scheduler.unscheduleJob(triggerCorte(sesionId));
            // Los jobs son storeDurably (programarSiFalta): hay que borrarlos
            // explícitamente o el re-agendar chocaría con el mismo identity.
            scheduler.deleteJob(new JobKey(prefijoDe(CrearSalaJob.class) + "-job-" + sesionId, GRUPO_JOB));
            scheduler.deleteJob(new JobKey(prefijoDe(NoShowJob.class) + "-job-" + sesionId, GRUPO_JOB));
            scheduler.deleteJob(new JobKey(prefijoDe(CorteAutomaticoJob.class) + "-job-" + sesionId, GRUPO_JOB));
        } catch (SchedulerException e) {
            // Fail-closed: la cancelación/reprogramación no puede colarse si no
            // logramos limpiar los jobs viejos.
            throw new IllegalStateException(
                    "No se pudieron desagendar los jobs de la sesión " + sesionId, e);
        }
    }

    /** Crea la sala en LiveKit (T-5, {@link CrearSalaJob}). Idempotente y fail-safe:
     *  si la Reserva ya no está confirmada (cancelada/no-show), no crea nada. */
    @Transactional
    public void crearSalaDiferida(UUID sesionId) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId).orElse(null);
        if (sesion == null || sesion.getLivekitRoomId() != null) {
            return;
        }
        Reserva reserva = reservaRepo.findById(sesion.getReservaId()).orElse(null);
        if (reserva == null || reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            return;
        }
        sesion.setLivekitRoomId(liveKitService.crearSala("sesion-" + sesion.getId()));
        sesionRepo.save(sesion);
    }

    // ------------------------------------------------ no-show (T-M3-04)

    /**
     * Decide el no-show a T+10 consultando los joins grabados por el webhook
     * (nunca en memoria). Marca la Reserva y la Sesión, emite el evento hacia M5
     * y cancela el propio trigger. Idempotente: si la Reserva ya dejó de estar
     * confirmada, no hace nada.
     */
    @Transactional
    public EstadoReserva ejecutarNoShow(UUID sesionId) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId)
                .orElseThrow(SesionNoEncontradaException::new);
        Reserva reserva = reservaRepo.findById(sesion.getReservaId())
                .orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            return reserva.getEstado();
        }

        boolean tutorEntro = sesion.getTutorJoinedAt() != null;
        boolean estudianteEntro = sesion.getEstudianteJoinedAt() != null;
        if (tutorEntro && estudianteEntro) {
            cancelarNoShow(sesion.getId()); // llegaron en el último momento
            return reserva.getEstado();
        }

        EstadoReserva nuevoEstado;
        SesionEvento evento;
        if (!tutorEntro && !estudianteEntro) {
            nuevoEstado = EstadoReserva.NO_SHOW_DOBLE;
            evento = new SesionNoShowDobleEvent(this, reserva.getId());
        } else if (tutorEntro) {
            nuevoEstado = EstadoReserva.NO_SHOW_ESTUDIANTE; // faltó el estudiante
            evento = new SesionNoShowEstudianteEvent(this, reserva.getId());
        } else {
            nuevoEstado = EstadoReserva.NO_SHOW_TUTOR; // faltó el tutor
            evento = new SesionNoShowTutorEvent(this, reserva.getId());
        }

        reserva.setEstado(nuevoEstado);
        reservaRepo.save(reserva);

        sesion.setEstado(SesionAprendizaje.ESTADO_FINALIZADA_ANTICIPADA);
        sesion.setFinReal(Instant.now());
        sesion.setDuracionEfectivaSegundos(0);
        sesionRepo.save(sesion);

        events.publishEvent(evento);
        cancelarNoShow(sesion.getId());
        return nuevoEstado;
    }

    // ------------------------------------------------ token de acceso (M3-frontend)

    // FIXME AUD-001/AUD-003 (auditoría 2026-09-21): (a) no hay guard de estado — devuelve token
    // para una sesión ya cortada por kill-switch; (b) la identidad del participante es el DNI,
    // que LiveKit difunde al otro participante y el frontend renderiza en pantalla. Con menores
    // esto es un dato sensible bajo Ley 25.326. Se corrige en FASE 1.
    /**
     * Devuelve el token de LiveKit para que el participante se conecte a la sala.
     * Misma autorización que {@link #finalizar}: solo tutor, beneficiario o pagador.
     * La sala debe haber sido creada ya (T-5, {@link com.tinku.aula.jobs.CrearSalaJob}).
     */
    public String[] obtenerToken(Usuario usuario, UUID sesionId) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId)
                .orElseThrow(SesionNoEncontradaException::new);
        if (sesion.getLivekitRoomId() == null) {
            throw new SesionSinSalaException("La sala aún no fue creada (T-5 no alcanzado).");
        }
        Reserva reserva = reservaRepo.findById(sesion.getReservaId())
                .orElseThrow(ReservaNoEncontradaException::new);
        if (!esParticipante(reserva, usuario)) {
            throw new SoloParticipanteException();
        }
        String token = liveKitService.generarTokenParticipante(
                usuario.getDni(), sesion.getLivekitRoomId());
        return new String[]{token, sesion.getLivekitRoomId()};
    }

    /**
     * Resuelve la Sesión a partir de su Reserva — el sentido inverso de
     * {@link #obtenerToken}, que necesita el frontend para poder armar un botón
     * "Entrar a la clase"/"Calificar" desde la pantalla de una Reserva sin ya
     * conocer el id de la Sesión de antemano (antes no existía ningún camino
     * para esto: {@code ReservaResponse} no trae {@code sesionId} y no había
     * endpoint que resolviera la relación 1:1 que sí existe en el modelo,
     * {@code sesion_aprendizaje.reserva_id UNIQUE}).
     *
     * Se autoriza ANTES de revelar si la Sesión existe — un tercero no
     * participante recibe 403 sin importar si ya se programó la Sesión o no,
     * para no filtrar ese dato a quien no tiene por qué verlo.
     */
    public SesionAprendizaje obtenerPorReserva(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(ReservaNoEncontradaException::new);
        if (!esParticipante(reserva, usuario)) {
            throw new SoloParticipanteException();
        }
        return sesionRepo.findByReservaId(reservaId)
                .orElseThrow(SesionNoEncontradaException::new);
    }

    // ------------------------------------------------ finalización (T-M3-05)

    /** US-8: el botón «Finalizar» — solo tutor, beneficiario o pagador (403 si no). */
    @Transactional
    public SesionAprendizaje finalizar(Usuario usuario, UUID sesionId) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId)
                .orElseThrow(SesionNoEncontradaException::new);
        Reserva reserva = reservaRepo.findById(sesion.getReservaId())
                .orElseThrow(ReservaNoEncontradaException::new);
        if (!esParticipante(reserva, usuario)) {
            throw new SoloParticipanteException();
        }
        return marcarFinalizada(sesion, reserva);
    }

    /** US-8: corte automático a {@code fin_agendado + 5min} ({@link CorteAutomaticoJob}). */
    @Transactional
    public void ejecutarCorteAutomatico(UUID sesionId) {
        sesionRepo.findById(sesionId).ifPresent(sesion -> {
            Reserva reserva = reservaRepo.findById(sesion.getReservaId()).orElse(null);
            if (reserva == null
                    || (reserva.getEstado() != EstadoReserva.CONFIRMADA
                        && reserva.getEstado() != EstadoReserva.EN_CURSO)) {
                return; // ya cerrada: repetir no cambia nada
            }
            if (esCorteAntesDel50(sesion)) {
                marcarInterrumpida(sesion, reserva);
            } else {
                marcarFinalizada(sesion, reserva);
            }
        });
    }

    /** US-5 (FR-AULA-005): la duración efectiva al corte es < 50% de la agendada
     *  (la agendada se congeló al programar, {@code duracionAgendadaSegundos}). */
    private boolean esCorteAntesDel50(SesionAprendizaje sesion) {
        if (sesion.getDuracionAgendadaSegundos() == null
                || sesion.getDuracionAgendadaSegundos() <= 0) {
            return false; // sin umbral conocido, se cierra normal
        }
        long efectiva = sesion.getInicioReal() != null
                ? Math.max(0, Duration.between(sesion.getInicioReal(), Instant.now()).getSeconds())
                : 0;
        return efectiva * 2 < sesion.getDuracionAgendadaSegundos();
    }

    /**
     * Quita del scheduler el job de no-show (ya no hace falta decidir: ambos se
     * unieron, o la sesión ya se cerró). Si el unschedule falla la seguridad NO
     * cae: {@code ejecutarNoShow} es idempotente y detecta ese caso.
     */
    public void cancelarNoShow(UUID sesionId) {
        try {
            scheduler.unscheduleJob(triggerNoShow(sesionId));
        } catch (SchedulerException e) {
            // benigna por los guards de estado — ver javadoc de ejecutarNoShow.
        }
    }

    private SesionAprendizaje marcarFinalizada(SesionAprendizaje sesion, Reserva reserva) {
        if (reserva.getEstado() == EstadoReserva.FINALIZADA
                || SesionAprendizaje.ESTADO_FINALIZADA.equals(sesion.getEstado())) {
            return sesion; // botón + job de corte pueden chocar: el segundo no re-emite
        }
        Instant fin = Instant.now();
        long duracion = sesion.getInicioReal() != null
                ? Math.max(0, Duration.between(sesion.getInicioReal(), fin).getSeconds())
                : 0;
        sesion.setEstado(SesionAprendizaje.ESTADO_FINALIZADA);
        sesion.setFinReal(fin);
        sesion.setDuracionEfectivaSegundos((int) duracion);
        sesionRepo.save(sesion);

        reserva.setEstado(EstadoReserva.FINALIZADA);
        reservaRepo.save(reserva);

        events.publishEvent(new SesionFinalizadaEvent(
                this, reserva.getId(), fin));
        cancelarNoShow(sesion.getId());
        return sesion;
    }

    /**
     * US-5/FR-AULA-005 — corte del {@code CorteAutomaticoJob} antes del 50%.
     * Emite {@code sesion.interrumpida} (M5 reembolsa, FR-PAG-004) y deja la
     * Sesión en {@code interrumpida} (el CHECK de V8 lo permite). Idempotente
     * por el mismo guard de {@code marcarFinalizada}: la Reserva pasada a
     * FINALIZADA bloquea cualquier segundo cierre (finalizada o interrumpida).
     */
    private SesionAprendizaje marcarInterrumpida(SesionAprendizaje sesion, Reserva reserva) {
        if (reserva.getEstado() == EstadoReserva.FINALIZADA) {
            return sesion;
        }
        Instant fin = Instant.now();
        long duracion = sesion.getInicioReal() != null
                ? Math.max(0, Duration.between(sesion.getInicioReal(), fin).getSeconds())
                : 0;
        sesion.setEstado(SesionAprendizaje.ESTADO_INTERRUMPIDA);
        sesion.setFinReal(fin);
        sesion.setDuracionEfectivaSegundos((int) duracion);
        sesionRepo.save(sesion);

        reserva.setEstado(EstadoReserva.FINALIZADA);
        reservaRepo.save(reserva);

        events.publishEvent(new SesionInterrumpidaEvent(this, reserva.getId()));
        cancelarNoShow(sesion.getId());
        return sesion;
    }

    // ------------------------------------------------ kill-switch (T-M3-07/08/09)

    // FIXME AUD-005 (auditoría 2026-09-21): el único control es esParticipante(). Cualquiera de
    // los tres puede disparar el corte contra otro, sin evidencia y sin límite de tasa; en rama
    // menor eso produce reembolso total + suspensión del Tutor. Ver FASE 1 y el anexo pendiente
    // a ADR-M3-01 (modelo de amenaza del clasificador on-device).
    /**
     * Disparo del kill-switch (T-M3-07, US-6/US-7, FR-AULA-009). El backend
     * decide la rama con datos propios de M1: si el {@code beneficiario} de la
     * Reserva es un MENOR → rama menor (corte directo, Artículo II); si es
     * adulto → rama adultos (blur + pregunta al otro participante). El request
     * NO puede forzar la rama (no la acepta). {@code detectadoId} = el usuario
     * cuyo contenido fue clasificado como inapropiado.
     */
    @Transactional
    public SesionAprendizaje ejecutarKillswitch(Usuario usuario, UUID sesionId, UUID detectadoId) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId)
                .orElseThrow(SesionNoEncontradaException::new);
        Reserva reserva = reservaRepo.findById(sesion.getReservaId())
                .orElseThrow(ReservaNoEncontradaException::new);
        if (!esParticipante(reserva, usuario)) {
            throw new SoloParticipanteException();
        }
        if (detectadoId == null
                || (!reserva.getTutor().getId().equals(detectadoId)
                    && !reserva.getBeneficiario().getId().equals(detectadoId))) {
            throw new DetectadoInvalidoException(detectadoId);
        }
        // Idempotente solo mientras hay un ciclo SIN resolver: rama menor ya
        // generó Alerta (sesión cortada, no hay nada que re-disparar), o rama
        // adultos con una confirmación todavía esperando respuesta (evita
        // duplicar la pregunta mientras está pendiente). Auditoría 2026-09-18:
        // antes este guard también bloqueaba tras una confirmación YA resuelta
        // con vio=false (falso positivo) — dejaba el kill-switch inutilizado
        // para el resto de la sesión, violando Spec_M3 US-7 ("No" → continúa
        // el monitoreo normal). Una detección real posterior en la misma
        // sesión nunca volvía a generar nada.
        boolean confirmacionPendiente = confirmacionRepo.findBySesionId(sesionId)
                .map(c -> c.getRespondidoId() == null)
                .orElse(false);
        if (alertaRepo.findBySesionId(sesionId).isPresent() || confirmacionPendiente) {
            return sesion;
        }
        if (reserva.getBeneficiario().getTipo() == TipoUsuario.MENOR) {
            return ramaMenor(sesion, reserva, detectadoId);
        }
        return ramaAdultos(sesion, reserva, detectadoId);
    }

    // FIXME AUD-006 (auditoría 2026-09-21): suspende siempre a reserva.getTutor(), ignorando
    // detectadoId. Cuando el detectado es el menor (caso previsto en Spec_M3 US-6), el Tutor
    // queda suspendido y AlertaSeguridadService.resolver() nunca lo reactiva, porque resuelve
    // mirando alerta.getDetectadoId(). Comparar con confirmarRamaAdultos(), que sí usa el
    // detectado. Se corrige en FASE 1.
    /**
     * US-6 — rama MENOR: corte directo, sin confirmación ni pregunta al menor
     * (Artículo II). Alerta de Seguridad {@code rama=menor}, suspensión
     * preventiva del Tutor ({@code activo_para_matching=false}, FR-SEC-004) y
     * evento {@code sesion.killswitch_menor}.
     */
    private SesionAprendizaje ramaMenor(SesionAprendizaje sesion, Reserva reserva,
                                        UUID detectadoId) {
        Usuario tutor = reserva.getTutor();
        tutor.setActivoParaMatching(false);
        usuarioRepo.save(tutor);

        AlertaSeguridad alerta = new AlertaSeguridad();
        alerta.setSesionId(sesion.getId());
        alerta.setRama("menor");
        alerta.setDetectadoId(detectadoId);
        alertaRepo.save(alerta);

        cortar(sesion, reserva);
        events.publishEvent(new SesionKillswitchMenorEvent(this, reserva.getId(), detectadoId));
        cancelarNoShow(sesion.getId());
        return sesion;
    }

    /**
     * US-7 — rama ADULTOS: NO corta. Registra la confirmación en estado
     * "esperando" (el otro participante responde si vio algo); la sesión sigue
     * con el video del detectado en blur (frontend). Si después responde "No"
     * continúa normal; "Sí" corta ({@link #confirmarRamaAdultos}).
     */
    private SesionAprendizaje ramaAdultos(SesionAprendizaje sesion, Reserva reserva,
                                          UUID detectadoId) {
        // sesion_id es UNIQUE en BD (V12): un segundo disparo tras un falso
        // positivo ya resuelto (vio=false) reutiliza y resetea la misma fila
        // en vez de intentar un segundo INSERT — ver nota de ejecutarKillswitch.
        ConfirmacionKillswitch confirmacion = confirmacionRepo.findBySesionId(sesion.getId())
                .orElseGet(ConfirmacionKillswitch::new);
        confirmacion.setSesionId(sesion.getId());
        confirmacion.setDetectadoId(detectadoId);
        confirmacion.setRespondidoId(null);
        confirmacion.setVio(null);
        confirmacion.setRespondedAt(null);
        confirmacionRepo.save(confirmacion);
        return sesion;
    }

    /**
     * Confirmación de la rama adultos (T-M3-09): la responde el participante
     * que NO generó la detección. {@code vio=false} → la sesión continúa (solo
     * queda el registro en la BD); {@code vio=true} → corte, bloqueo del
     * detectado, Alerta {@code rama=adultos} y evento
     * {@code sesion.killswitch_adultos}.
     */
    @Transactional
    public SesionAprendizaje confirmarRamaAdultos(Usuario usuario, UUID sesionId, boolean vio) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId)
                .orElseThrow(SesionNoEncontradaException::new);
        Reserva reserva = reservaRepo.findById(sesion.getReservaId())
                .orElseThrow(ReservaNoEncontradaException::new);
        if (!esParticipante(reserva, usuario)) {
            throw new SoloParticipanteException();
        }
        ConfirmacionKillswitch confirmacion = confirmacionRepo.findBySesionId(sesionId)
                .orElseThrow(ConfirmacionNoPendienteException::new);
        if (confirmacion.getRespondidoId() != null
                || confirmacion.getDetectadoId().equals(usuario.getId())) {
            throw new ConfirmacionNoPendienteException();
        }
        confirmacion.setRespondidoId(usuario.getId());
        confirmacion.setVio(vio);
        confirmacion.setRespondedAt(Instant.now());
        confirmacionRepo.save(confirmacion);

        if (!vio) {
            return sesion; // "No": la sesión continúa, el evento queda logueado (fila)
        }
        // "Sí": corte + bloqueo del detectado + Alerta + evento.
        Usuario detectado = usuarioRepo.findById(confirmacion.getDetectadoId()).orElse(null);
        if (detectado != null) {
            detectado.setActivoParaMatching(false);
            usuarioRepo.save(detectado);
        }
        AlertaSeguridad alerta = new AlertaSeguridad();
        alerta.setSesionId(sesion.getId());
        alerta.setRama("adultos");
        alerta.setDetectadoId(confirmacion.getDetectadoId());
        alertaRepo.save(alerta);

        cortar(sesion, reserva);
        events.publishEvent(new SesionKillswitchAdultosEvent(
                this, reserva.getId(), confirmacion.getDetectadoId()));
        cancelarNoShow(sesion.getId());
        return sesion;
    }

    /**
     * Subida de la evidencia del kill-switch (T-M3-08): solo la REFERENCIA al
     * clip de 30s (Artículo V, BR-KS-01). Solo alcanzable tras un kill-switch
     * ya registrado (existe la Alerta) — si no → 404 {@link
     * AlertaNoEncontradaException}.
     */
    @Transactional
    public AlertaSeguridad subirEvidencia(Usuario usuario, UUID sesionId, String clipUrl,
                                          Integer duracionSegundos) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId)
                .orElseThrow(SesionNoEncontradaException::new);
        Reserva reserva = reservaRepo.findById(sesion.getReservaId())
                .orElseThrow(ReservaNoEncontradaException::new);
        if (!esParticipante(reserva, usuario)) {
            throw new SoloParticipanteException();
        }
        if (!(clipUrl.startsWith("https://") || clipUrl.startsWith("http://"))) {
            throw new EvidenciaInvalidaException("la URL del clip debe ser http(s).");
        }
        if (duracionSegundos != null && (duracionSegundos <= 0 || duracionSegundos > 30)) {
            throw new EvidenciaInvalidaException(
                    "el clip del buffer tiene un máximo de 30 segundos (BR-KS-01).");
        }
        AlertaSeguridad alerta = alertaRepo.findBySesionId(sesionId)
                .orElseThrow(AlertaNoEncontradaException::new);
        alerta.setClipUrl(clipUrl);
        return alertaRepo.save(alerta);
    }

    // FIXME AUD-001 (auditoría 2026-09-21): este método NO cierra la sala de LiveKit. Solo
    // persiste el estado. La sala sigue viva y los tokens emitidos siguen siendo válidos hasta
    // su TTL. Spec_M3 US-6 exige "la sesión se corta para ambos". Se corrige en FASE 1.
    /**
     * Cierre de la sesión SIN emitir evento (lo emite cada rama del kill-switch
     * con su nombre exacto). Idempotente por los guards de estado: si ya está
     * finalizada/interrumpida, no re-marca ni permite doble evento.
     */
    private void cortar(SesionAprendizaje sesion, Reserva reserva) {
        if (reserva.getEstado() == EstadoReserva.FINALIZADA
                || SesionAprendizaje.ESTADO_FINALIZADA.equals(sesion.getEstado())
                || SesionAprendizaje.ESTADO_INTERRUMPIDA.equals(sesion.getEstado())) {
            return;
        }
        Instant fin = Instant.now();
        long duracion = sesion.getInicioReal() != null
                ? Math.max(0, Duration.between(sesion.getInicioReal(), fin).getSeconds())
                : 0;
        sesion.setEstado(SesionAprendizaje.ESTADO_FINALIZADA);
        sesion.setFinReal(fin);
        sesion.setDuracionEfectivaSegundos((int) duracion);
        sesionRepo.save(sesion);
        reserva.setEstado(EstadoReserva.FINALIZADA);
        reservaRepo.save(reserva);
    }

    private boolean esParticipante(Reserva reserva, Usuario usuario) {
        return reserva.getTutor().getId().equals(usuario.getId())
                || reserva.getBeneficiario().getId().equals(usuario.getId())
                || (reserva.getPagador() != null
                    && reserva.getPagador().getId().equals(usuario.getId()));
    }

    // ------------------------------------------------ agendar jobs de Quartz

    /**
     * Programa un job puntual en el JobStore persistido (mismo patrón que
     * SolicitudService.programarExpiracion) a menos que ese trigger ya exista.
     */
    private void programarSiFalta(UUID sesionId, Class<? extends Job> jobClass,
                                  Instant disparo) {
        String prefijo = prefijoDe(jobClass);
        TriggerKey key = triggerKey(prefijo, sesionId);
        try {
            if (scheduler.checkExists(key)) {
                return;
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException(
                    "No se pudo consultar el job de la sesión " + sesionId, e);
        }
        JobDetail detail = JobBuilder.newJob(jobClass)
                .withIdentity(prefijo + "-job-" + sesionId, GRUPO_JOB)
                .usingJobData(SesionJobKeys.PARAM_SESION_ID, sesionId.toString())
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(key)
                .startAt(Date.from(disparo))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionIgnoreMisfires())
                .build();
        try {
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            // La transacción que confirma la Reserva se aborta (fail-closed): una
            // Reserva confirmada sin sus jobs no puede quedar en silencio.
            throw new IllegalStateException(
                    "No se pudo agendar el job de la sesión " + sesionId, e);
        }
    }

    private static String prefijoDe(Class<? extends Job> jobClass) {
        if (jobClass == CrearSalaJob.class) return "sala";
        if (jobClass == NoShowJob.class) return "no-show";
        if (jobClass == CorteAutomaticoJob.class) return "corte";
        throw new IllegalArgumentException("Job de sesión desconocido: " + jobClass);
    }

    // ------------------------------------------------ identidades (compartidas)

    /** Clave del trigger de no-show — la usa el webhook para cancelarlo (T-M3-04). */
    public static TriggerKey triggerNoShow(UUID sesionId) {
        return triggerKey("no-show", sesionId);
    }

    /** Clave del trigger de creación de sala (T-M3-03) — tests. */
    public static TriggerKey triggerSala(UUID sesionId) {
        return triggerKey("sala", sesionId);
    }

    /** Clave del trigger de corte automático (T-M3-05) — tests. */
    public static TriggerKey triggerCorte(UUID sesionId) {
        return triggerKey("corte", sesionId);
    }

    private static TriggerKey triggerKey(String prefijo, UUID sesionId) {
        return new TriggerKey(prefijo + "-trigger-" + sesionId, GRUPO_JOB);
    }

    /**
     * Nombre del parámetro compartido por los 3 jobs de sesión en su JobDataMap
     * (centralizado acá para que service y jobs nunca se desacoplen en el nombre).
     */
    public static final class SesionJobKeys {
        public static final String PARAM_SESION_ID = "sesionId";

        private SesionJobKeys() {
        }
    }
}