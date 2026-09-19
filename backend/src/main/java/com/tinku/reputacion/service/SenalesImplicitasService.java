package com.tinku.reputacion.service;

import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.pagos.evento.SesionFinalizadaEvent;
import com.tinku.pagos.evento.SesionNoShowEstudianteEvent;
import com.tinku.pagos.evento.SesionNoShowTutorEvent;
import com.tinku.reputacion.jobs.RecordatorioCalificacionJob;
import com.tinku.reputacion.model.SenalesImplicitasTutor;
import com.tinku.reputacion.repository.SenalesImplicitasTutorRepository;
import com.tinku.reservas.evento.ReservaCanceladaEvent;
import com.tinku.reservas.evento.ReservaConfirmadaEvent;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Conserva el agregado {@code senales_implicitas_tutor} (ADR-M7-01: sin log
 * crudo, recalculado incremental) a partir de los eventos de negocio que ya
 * emiten M3/M4/M5, y agenda el recordatorio unico de calificacion (T-M7-06).
 *
 * Formulas incrementales (version simple, ver ADR-M7-01):
 *  - evento "puntual" (sesion finalizada o no-show del estudiante): la
 *    puntualidad promedia hacia 1.0 ({@code (1+viejo)/2}).
 *  - evento "impuntual" (no-show del tutor): la puntualidad promedia hacia 0
 *    ({@code viejo/2}) y ademas sube la tasa de cancelacion/no-show.
 *  - cancelacion manual en la que el Tutor es quien cancela: sube la tasa.
 *  - reserva confirmada con historial del mismo Tutor+beneficiario: re-enganche.
 *
 * El peso que consume M2 se calcula en {@link ReputacionSignalProveedorImpl}.
 * {@code tiempo_respuesta_promedio_min} no se alimenta todavia: no existe
 * modulo de mensajeria; queda en 0 hasta que haya una fuente.
 */
@Component
public class SenalesImplicitasService {

    private static final Logger log = LoggerFactory.getLogger(SenalesImplicitasService.class);

    /** FR-REP-005 / T-M7-06 — recordatorio unico a 24hs del fin de sesion. */
    private static final Duration RECORDATORIO_24HS = Duration.ofHours(24);

    private static final String GRUPO_QUARTZ = "m7-reputacion";

    private final SenalesImplicitasTutorRepository senalesRepo;
    private final ReservaRepository reservaRepo;
    private final SesionAprendizajeRepository sesionRepo;
    private final Scheduler scheduler;
    private final SenalesImplicitasGuard guard;

    public SenalesImplicitasService(SenalesImplicitasTutorRepository senalesRepo,
                                    ReservaRepository reservaRepo,
                                    SesionAprendizajeRepository sesionRepo,
                                    Scheduler scheduler,
                                    SenalesImplicitasGuard guard) {
        this.senalesRepo = senalesRepo;
        this.reservaRepo = reservaRepo;
        this.sesionRepo = sesionRepo;
        this.scheduler = scheduler;
        this.guard = guard;
    }

    /**
     * {@code sesion.finalizada} (M3): el Tutor realmente dicto la sesion →
     * puntual + cuenta como dictada, y agenda el recordatorio de calificacion
     * a las 24hs del fin (unico por sesion — las sesiones solo finalizan una
     * vez, Estado `finalizada`, FR-REP-008).
     */
    @EventListener
    @Transactional
    public void onSesionFinalizada(SesionFinalizadaEvent event) {
        UUID tutorId = tutorDe(event.getReservaId());
        if (tutorId != null) {
            actualizarSenalesSeguro(tutorId, () -> {
                SenalesImplicitasTutor s = obtenerOCrear(tutorId);
                s.setPuntualidadPromedio(promediarHaciaUno(s.getPuntualidadPromedio()));
                s.setSesionesDictadasTotal(s.getSesionesDictadasTotal() + 1);
                senalesRepo.save(s);
            });
        }
        programarRecordatorio(event);
    }

    /** {@code sesion.no_show_estudiante}: el Tutor se presento aunque el
     *  estudiante no — cuenta como puntualidad positiva. */
    @EventListener
    @Transactional
    public void onSesionNoShowEstudiante(SesionNoShowEstudianteEvent event) {
        UUID tutorId = tutorDe(event.getReservaId());
        if (tutorId == null) return;
        actualizarSenalesSeguro(tutorId, () -> {
            SenalesImplicitasTutor s = obtenerOCrear(tutorId);
            s.setPuntualidadPromedio(promediarHaciaUno(s.getPuntualidadPromedio()));
            senalesRepo.save(s);
        });
    }

    /** {@code sesion.no_show_tutor}: el Tutor no se presento → impuntual y
     *  sube su tasa de cancelacion/no-show. */
    @EventListener
    @Transactional
    public void onSesionNoShowTutor(SesionNoShowTutorEvent event) {
        UUID tutorId = tutorDe(event.getReservaId());
        if (tutorId == null) return;
        actualizarSenalesSeguro(tutorId, () -> {
            SenalesImplicitasTutor s = obtenerOCrear(tutorId);
            s.setPuntualidadPromedio(promediarHaciaCero(s.getPuntualidadPromedio()));
            s.setTasaCancelacionNoshow(promediarHaciaUno(s.getTasaCancelacionNoshow()));
            senalesRepo.save(s);
        });
    }

    /** {@code reserva.cancelada}: si quien cancela es el propio Tutor, es
     *  cancelacion a su nombre (el estudiante no tuvo la oportunidad de
     *  calificar). Si quien cancela es el AR o el estudiante, no penaliza. */
    @EventListener
    @Transactional
    public void onReservaCancelada(ReservaCanceladaEvent event) {
        UUID tutorId = tutorDe(event.getReservaId());
        if (tutorId == null || !tutorId.equals(event.getCanceladaPorUsuarioId())) return;
        actualizarSenalesSeguro(tutorId, () -> {
            SenalesImplicitasTutor s = obtenerOCrear(tutorId);
            s.setTasaCancelacionNoshow(promediarHaciaUno(s.getTasaCancelacionNoshow()));
            senalesRepo.save(s);
        });
    }

    /** {@code reserva.confirmada}: si el estudiante ya tenia una reserva con
     *  ese Tutor, es re-enganche (señal fuerte de confianza). */
    @EventListener
    @Transactional
    public void onReservaConfirmada(ReservaConfirmadaEvent event) {
        Reserva reserva = reservaRepo.findById(event.getReservaId()).orElse(null);
        if (reserva == null) return;
        UUID tutorId = reserva.getTutor().getId();
        long historial = reservaRepo
                .findByTutor_IdAndBeneficiario_Id(tutorId, reserva.getBeneficiario().getId())
                .size();
        if (historial >= 2) {
            actualizarSenalesSeguro(tutorId, () -> {
                SenalesImplicitasTutor s = obtenerOCrear(tutorId);
                s.setTasaRecontratacion(promediarHaciaUno(s.getTasaRecontratacion()));
                senalesRepo.save(s);
            });
        }
    }

    /**
     * Auditoría 2026-09-18: dos sesiones del mismo Tutor finalizando casi al
     * mismo instante disparan dos transacciones concurrentes de
     * {@code obtenerOCrear} → {@code findById} vacío en ambas → dos INSERT con
     * el mismo PK ({@code tutor_id}, sin fila previa) → una de las dos revienta
     * con {@link DataAccessException}. Sin este guard, esa excepción viajaba
     * sin capturar hasta la transacción de {@code SesionService.finalizar()}
     * (el listener corre síncrono, misma transacción, Artículo IX) y la hacía
     * rollback completa: la Sesión no quedaba {@code finalizada}, no se emitía
     * el evento, y el timer de liberación de escrow de M5 nunca arrancaba — un
     * bug de reputación (señal blanda, no expuesta al usuario) bloqueando un
     * flujo de dinero real. Delegado a {@link SenalesImplicitasGuard}
     * (bean separado, no auto-invocación) para que
     * {@code Propagation.NESTED} pase realmente por el proxy transaccional de
     * Spring y acote el rollback a un SAVEPOINT de esta sola operación — la
     * transacción de {@code finalizar()} sigue su curso normal.
     */
    private void actualizarSenalesSeguro(UUID tutorId, Runnable accion) {
        guard.ejecutarEnSavepoint(tutorId, accion);
    }

    private void programarRecordatorio(SesionFinalizadaEvent event) {
        sesionRepo.findByReservaId(event.getReservaId()).ifPresent(sesion -> {
            TriggerKey key = TriggerKey.triggerKey("recordatorio-calificacion-" + sesion.getId(), GRUPO_QUARTZ);
            try {
                if (scheduler.checkExists(key)) {
                    return;
                }
                JobDetail detail = JobBuilder.newJob(RecordatorioCalificacionJob.class)
                        .withIdentity("recordatorio-calificacion-" + sesion.getId(), GRUPO_QUARTZ)
                        .usingJobData(RecordatorioCalificacionJob.PARAM_SESION_ID, sesion.getId().toString())
                        .storeDurably()
                        .build();
                Trigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(key)
                        .startAt(Date.from(event.getTimestampFin().plus(RECORDATORIO_24HS)))
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withMisfireHandlingInstructionIgnoreMisfires())
                        .build();
                scheduler.scheduleJob(detail, trigger);
            } catch (SchedulerException e) {
                // Un recordatorio perdido no bloquea el negocio: el bloqueo por
                // calificacion pendiente (FR-REP-006) sigue cubriendo el caso.
                log.warn("No se pudo programar el recordatorio de calificacion de la sesion {}",
                        sesion.getId(), e);
            }
        });
    }

    private UUID tutorDe(UUID reservaId) {
        return reservaRepo.findById(reservaId).map(r -> r.getTutor().getId()).orElse(null);
    }

    private SenalesImplicitasTutor obtenerOCrear(UUID tutorId) {
        return senalesRepo.findById(tutorId).orElseGet(() -> {
            SenalesImplicitasTutor nuevo = new SenalesImplicitasTutor();
            nuevo.setTutorId(tutorId);
            return nuevo;
        });
    }

    /** Desplazo el valor promedio hacia 1.0 (evento puntual). */
    private static BigDecimal promediarHaciaUno(BigDecimal viejo) {
        return BigDecimal.ONE.add(viejo).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    /** Desplazo el valor promedio hacia 0 (evento impuntual). */
    private static BigDecimal promediarHaciaCero(BigDecimal viejo) {
        return viejo.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
}