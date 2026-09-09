package com.tinku.aula;

import com.tinku.pagos.evento.SesionEvento;
import com.tinku.pagos.evento.SesionFinalizadaEvent;
import com.tinku.pagos.evento.SesionNoShowDobleEvent;
import com.tinku.pagos.evento.SesionNoShowEstudianteEvent;
import com.tinku.pagos.evento.SesionNoShowTutorEvent;
import com.tinku.aula.jobs.CorteAutomaticoJob;
import com.tinku.aula.jobs.CrearSalaJob;
import com.tinku.aula.jobs.NoShowJob;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.Usuario;
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
    private final FranjaService franjaService;
    private final LiveKitService liveKitService;
    private final Scheduler scheduler;
    private final ApplicationEventPublisher events;

    public SesionService(SesionAprendizajeRepository sesionRepo,
                         ReservaRepository reservaRepo,
                         FranjaService franjaService,
                         LiveKitService liveKitService,
                         Scheduler scheduler,
                         ApplicationEventPublisher events) {
        this.sesionRepo = sesionRepo;
        this.reservaRepo = reservaRepo;
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
            marcarFinalizada(sesion, reserva);
        });
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