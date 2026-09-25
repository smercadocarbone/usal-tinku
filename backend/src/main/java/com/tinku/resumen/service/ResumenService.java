package com.tinku.resumen.service;

import com.tinku.seguridad.model.AlertaSeguridad;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.Usuario;
import com.tinku.aula.evento.SesionFinalizadaEvent;
import com.tinku.resumen.anonimizacion.AnonimizadorTranscript;
import com.tinku.resumen.jobs.RecordatorioResumenJob;
import com.tinku.resumen.jobs.ReintentoResumenJob;
import com.tinku.resumen.model.ResumenSesion;
import com.tinku.resumen.port.PromptResumen;
import com.tinku.resumen.port.ResumenProveedor;
import com.tinku.resumen.port.ResumenProveedorNoConfiguradoException;
import com.tinku.resumen.port.TranscriptSesionProveedor;
import com.tinku.resumen.repository.ResumenSesionRepository;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.repository.DenunciaRepository;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Modulo M6 — Resumen Automatico de la Sesion (T-M6-01 a T-M6-07).
 *
 * <p><b>T-M6-02 (listener):</b> escucha SOLO {@code sesion.finalizada}. Valida
 * {@code duracionEfectivaSegundos >= 600} (10 min efectivos, Tabla_Tiempos) y
 * recien ahi crea la fila {@code pendiente}. Si M3 cerro la sesion como
 * {@code sesion.interrumpida} o {@code sesion.killswitch_*} NO emite
 * {@code sesion.finalizada} (guards de {@code SesionService}) → no hay fila →
 * no hay resumen (caso borde #1 del Spec). Sin acoplamiento a como M3 calculo
 * la duracion: usa el dato ya persistido en la sesion.</p>
 *
 * <p><b>T-M6-03:</b> antes de crear la fila y ANTES de cada invocacion al
 * proveedor se consulta a M9 (Denuncia en {@code registrada}/{@code en_revision})
 * y a M3 (Alerta de Seguridad en {@code pendiente_revision}) para esa sesion.
 * Si existe cualquiera → {@code suspendido_seguridad} y el proveedor jamas se
 * invoca (nada sale al LLM; FR-SUM-008, BR-KS-03). El Spec no pide retomar la
 * generacion al resolverse la disputa (caso borde #4 solo aclara que un
 * reembolso no anula un resumen ya generado): lo minimo es pausar y no filtrar.</p>
 *
 * <p><b>T-M6-05:</b> la generacion va por {@link ResumenProveedor} (puerto,
 * GPT-4o por ADR-M6-03; sin LLM_PROVEEDOR=gpt-4o el bean es fail-closed). El transcript
 * pasa SIEMPRE por {@link AnonimizadorTranscript} antes de armar el prompt y
 * antes de cualquier llamada saliente (FR-SUM-005); el texto anonimizado queda
 * persistido aunque el LLM no exista (T-M6-04).</p>
 *
 * <p><b>T-M6-06:</b> si la llamada falla, backoff identico al de M5
 * ({@code LiberacionEscrowService}): 3 reintentos {@code 5min → 15min → 1h}
 * (Tabla_Tiempos, "Reintentos de liberacion" reutilizado por consistencia —
 * Articulo I). Al agotarse, la fila queda {@code reintento_agotado} sin
 * resumen final — la SESION no se marca fallida (FR-SUM-007), solo se loguea
 * para monitoreo. La excepcion {@code ResumenProveedorNoConfiguradoException}
 * no reintenta: una config ausente es deterministica, no transitoria.</p>
 *
 * <p><b>T-M6-07:</b> al generar, agenda un recordatorio unico a las 24hs
 * (Tabla_Tiempos) con {@code recordatorio_pendiente}; se envia una sola vez.
 * Sin infraestructura de notificaciones en el piloto, el envio es un log
 * estructurado (mismo criterio que {@code RecordatorioCalificacionJob} de M7).</p>
 */
@Service
public class ResumenService {

    private static final Logger log = LoggerFactory.getLogger(ResumenService.class);

    /** Grupo de jobs de M6 en el JOB_STORE (mismo estilo que m5-pagos). */
    public static final String GRUPO_JOB = "m6-resumen";

    /** Tabla_Tiempos: "Duración mínima para generar resumen — 10 min efectivos". */
    static final int DURACION_MINIMA_SEGUNDOS = 600;

    /** FR-SUM-007 / Tabla_Tiempos (fila de M5): 3 reintentos con backoff 5/15/1h. */
    static final int MAX_REINTENTOS = 3;

    static final Duration[] BACKOFF =
            {Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1)};

    /** Tabla_Tiempos (igual criterio que M7): recordatorio unico a 24hs. */
    static final Duration RECORDATORIO_24HS = Duration.ofHours(24);

    /** Caso borde #2 del Spec: transcript sin contenido util → no se genera. */
    private static final int MIN_LONGITUD_TRANSCRIPT = 20;

    /** La primera generacion se agenda a +30s del evento (y no en el instante),
     *  para que el job corra en una transaccion distinta y siempre posterior al
     *  commit de la fila creada por el listener. 30s son despreciables contra el
     *  SLA de <=10min de FR-SUM-007. Los reintentos usan 5/15/60min (BACKOFF). */
    private static final Duration DEMORA_PRIMERA_GENERACION = Duration.ofSeconds(30);

    private final ResumenSesionRepository resumenRepo;
    private final SesionAprendizajeRepository sesionRepo;
    private final ReservaRepository reservaRepo;
    private final DenunciaRepository denunciaRepo;
    private final AlertaSeguridadRepository alertaRepo;
    private final AnonimizadorTranscript anonimizador;
    private final TranscriptSesionProveedor transcriptProveedor;
    private final ResumenProveedor proveedor;
    private final Scheduler scheduler;

    public ResumenService(ResumenSesionRepository resumenRepo,
                          SesionAprendizajeRepository sesionRepo,
                          ReservaRepository reservaRepo,
                          DenunciaRepository denunciaRepo,
                          AlertaSeguridadRepository alertaRepo,
                          AnonimizadorTranscript anonimizador,
                          TranscriptSesionProveedor transcriptProveedor,
                          ResumenProveedor proveedor,
                          Scheduler scheduler) {
        this.resumenRepo = resumenRepo;
        this.sesionRepo = sesionRepo;
        this.reservaRepo = reservaRepo;
        this.denunciaRepo = denunciaRepo;
        this.alertaRepo = alertaRepo;
        this.anonimizador = anonimizador;
        this.transcriptProveedor = transcriptProveedor;
        this.proveedor = proveedor;
        this.scheduler = scheduler;
    }

    // ------------------------------------------------------- consulta (T-M6-08)

    /**
     * Resumen visible para un participante de la Sesión (auditoría
     * 2026-09-20): el módulo generaba el resumen, pero no existía NINGÚN
     * endpoint para consultarlo — toda la funcionalidad de M6 era
     * inalcanzable para cualquier cliente. Mismo criterio de autorización que
     * M3/M7 (tutor, beneficiario o pagador).
     *
     * <p>{@code null} = "no disponible" y cubre TANTO que todavía no exista
     * fila (sesión corta, sin finalizar, o el listener recién no corrió) COMO
     * que exista pero no esté {@code generado} (pendiente/fallido/reintento
     * agotado/suspendido). Deliberado no distinguir estos casos en la
     * respuesta: un resumen {@code suspendido_seguridad} por una denuncia o
     * alerta activa (T-M6-03) queda "reservado a M9" — filtrarle a un
     * participante regular que existe una suspensión de seguridad activa
     * sobre SU sesión sería revelar el estado de una investigación en curso.</p>
     */
    public ResumenSesion obtenerParaParticipante(Usuario usuario, UUID sesionId) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId)
                .orElseThrow(ResumenSesionNoEncontradaException::new);
        Reserva reserva = reservaRepo.findById(sesion.getReservaId())
                .orElseThrow(ResumenSesionNoEncontradaException::new);
        if (!esParticipante(reserva, usuario)) {
            throw new ResumenNoPermitidoException();
        }
        return resumenRepo.findBySesionId(sesionId)
                .filter(r -> ResumenSesion.ESTADO_GENERADO.equals(r.getEstado()))
                .orElse(null);
    }

    private boolean esParticipante(Reserva reserva, Usuario usuario) {
        return reserva.getTutor().getId().equals(usuario.getId())
                || reserva.getBeneficiario().getId().equals(usuario.getId())
                || (reserva.getPagador() != null
                    && reserva.getPagador().getId().equals(usuario.getId()));
    }

    // ------------------------------------------------------- listener (T-M6-02)

    /**
     * {@code sesion.finalizada} (M3 → M6): si la duracion efectiva ya
     * persistida en la Sesion es >= 10 min, crea la fila y agenda la primera
     * generacion. Con duracion menor (o interrumpida/kill-switch, que nunca
     * emiten este evento) NO se crea ni siquiera la fila {@code pendiente}.
     * Idempotente por la unicidad de {@code sesion_id} (FR-SUM-006).
     */
    @EventListener
    @Transactional
    public void onSesionFinalizada(SesionFinalizadaEvent event) {
        sesionRepo.findByReservaId(event.getReservaId())
                .filter(s -> s.getDuracionEfectivaSegundos() != null
                        && s.getDuracionEfectivaSegundos() >= DURACION_MINIMA_SEGUNDOS)
                .ifPresent(sesion -> crearFilaOElegirSuspendido(sesion.getId()));
    }

    private void crearFilaOElegirSuspendido(UUID sesionId) {
        if (resumenRepo.findBySesionId(sesionId).isPresent()) {
            return; // FR-SUM-006: una sola generacion por sesion.
        }
        ResumenSesion fila = new ResumenSesion();
        fila.setSesionId(sesionId);
        if (suspendidoPorSeguridad(sesionId)) {
            // T-M6-03: denuncia o alerta activa → se pausa, el proveedor nunca se llama.
            fila.setEstado(ResumenSesion.ESTADO_SUSPENDIDO_SEGURIDAD);
            fila.setSuspendidoSeguridad(true);
            resumenRepo.save(fila);
            log.warn("RESUMEN_SUSPENDIDO sesionId={} — denuncia/alerta de seguridad activa, "
                    + "el material queda reservado a M9 (FR-SUM-008).", sesionId);
            return;
        }
        resumenRepo.save(fila);
        programarReintento(sesionId, Instant.now().plus(DEMORA_PRIMERA_GENERACION));
    }

    // --------------------------------------------- generacion + reintentos (T-M6-05/06)

    /**
     * Intenta la generacion del resumen de una sesion (lo invoca
     * {@code ReintentoResumenJob}). Idempotente y fail-closed:
     * <ol>
     *  <li>Suspension de seguridad re-verificada antes de tocar el proveedor.</li>
     *  <li>Transcript inutil → {@code fallido} (caso borde #2).</li>
     *  <li>El transcript pasa SIEMPRE por anonimizacion y queda persistido.</li>
     *  <li>Fallo transitorio → backoff de M5; sin proveedor → {@code reintento_agotado}.</li>
     * </ol>
     */
    @Transactional
    public void ejecutarGenerar(UUID sesionId) {
        ResumenSesion fila = resumenRepo.findBySesionId(sesionId).orElse(null);
        if (fila == null || !ResumenSesion.ESTADO_PENDIENTE.equals(fila.getEstado())) {
            return; // generado/suspendido/agotado ya → no-op
        }
        if (suspendidoPorSeguridad(sesionId)) {
            fila.setEstado(ResumenSesion.ESTADO_SUSPENDIDO_SEGURIDAD);
            fila.setSuspendidoSeguridad(true);
            resumenRepo.save(fila);
            cancelarReintento(sesionId);
            log.warn("RESUMEN_SUSPENDIDO sesionId={} — denuncia/alerta detectada antes de generar.",
                    sesionId);
            return;
        }
        String crudo = transcriptProveedor.transcript(sesionId);
        if (crudo == null || crudo.trim().length() < MIN_LONGITUD_TRANSCRIPT) {
            fila.setEstado(ResumenSesion.ESTADO_FALLIDO);
            resumenRepo.save(fila);
            cancelarReintento(sesionId);
            log.warn("RESUMEN_SIN_CONTENIDO sesionId={} — transcript no util; no se genera "
                    + "resumen ni se inventa contenido (caso borde #2).", sesionId);
            return;
        }
        // FR-SUM-005: la anonimizacion corre SIEMPRE y antes de toda llamada saliente.
        String anonimizado = anonimizador.anonimizar(crudo);
        fila.setTranscriptAnonimizado(anonimizado);
        fila.setPromptAnonimizado(armarPrompt(anonimizado));
        resumenRepo.save(fila);

        // El request al proveedor lleva UNICAMENTE el transcript ya anonimizado.
        ResumenProveedor.ResumenRequest request =
                new ResumenProveedor.ResumenRequest(sesionId, anonimizado, null, null, null, null);
        try {
            String texto = proveedor.generarResumen(request).texto();
            fila.setEstado(ResumenSesion.ESTADO_GENERADO);
            fila.setResumenFinal(texto);
            fila.setProximoReintentoAt(null);
            resumenRepo.save(fila);
            programarRecordatorio(sesionId);
            cancelarReintento(sesionId);
            log.info("RESUMEN_GENERADO sesionId={}", sesionId);
        } catch (ResumenProveedorNoConfiguradoException e) {
            // Fail-closed T-M6-05: sin proveedor la falla es deterministica; reintentar
            // 1h no va a configurar el ADR. Queda el estado de "sin resumen", no fallida.
            fila.setEstado(ResumenSesion.ESTADO_REINTENTO_AGOTADO);
            fila.setProximoReintentoAt(null);
            resumenRepo.save(fila);
            cancelarReintento(sesionId);
            log.warn("RESUMEN_SIN_PROVEEDOR sesionId={} — falta LLM_PROVEEDOR=gpt-4o o LLM_API_KEY "
                    + "(ADR-M6-03); el transcript anonimizado queda persistido.", sesionId);
        } catch (RuntimeException e) {
            reintentarOAgotar(fila);
        }
    }

    /** FR-SUM-007: registra el fallo y reprograma con backoff (5/15/1h), o agota. */
    private void reintentarOAgotar(ResumenSesion fila) {
        int intentos = fila.getIntentos() + 1;
        fila.setIntentos(intentos);
        if (intentos <= MAX_REINTENTOS) {
            Instant proximo = Instant.now().plus(BACKOFF[intentos - 1]);
            fila.setProximoReintentoAt(proximo);
            resumenRepo.save(fila);
            programarReintento(fila.getSesionId(), proximo);
            log.warn("RESUMEN_FALLO sesionId={} intento={} — reprogramado para {}", 
                    fila.getSesionId(), intentos, proximo);
        } else {
            fila.setProximoReintentoAt(null);
            fila.setEstado(ResumenSesion.ESTADO_REINTENTO_AGOTADO);
            resumenRepo.save(fila);
            cancelarReintento(fila.getSesionId());
            log.error("RESUMEN_REINTENTOS_AGOTADOS sesionId={} — sin resumen final; la sesion "
                    + "no se marca fallida (FR-SUM-007), queda para monitoreo.", fila.getSesionId());
        }
    }

    /** T-M6-03: denuncia activa (M9) o Alerta de Seguridad pendiente (M3) para la sesion. */
    private boolean suspendidoPorSeguridad(UUID sesionId) {
        if (denunciaRepo.existsBySesionIdAndEstadoIn(sesionId,
                List.of(EstadoDenuncia.REGISTRADA, EstadoDenuncia.EN_REVISION))) {
            return true;
        }
        return alertaRepo.existsBySesionIdAndEstado(sesionId,
                AlertaSeguridad.ESTADO_PENDIENTE_REVISION);
    }

    // --------------------------------------------------- recordatorio unico (T-M6-07)

    /**
     * Agenda el recordatorio unico del resumen a las 24hs (solo si la fila ya
     * quedo {@code generado}). Una sola vez: el flag {@code recordatorioPendiente}
     * impide re-agendar y el job one-shot no se reprograma.
     */
    @Transactional
    public void programarRecordatorio(UUID sesionId) {
        ResumenSesion fila = resumenRepo.findBySesionId(sesionId).orElse(null);
        if (fila == null || !ResumenSesion.ESTADO_GENERADO.equals(fila.getEstado())
                || fila.isRecordatorioPendiente()) {
            return;
        }
        fila.setRecordatorioPendiente(true);
        resumenRepo.save(fila);
        TriggerKey key = triggerRecordatorio(sesionId);
        try {
            if (scheduler.checkExists(key)) {
                return;
            }
            JobDetail detail = JobBuilder.newJob(RecordatorioResumenJob.class)
                    .withIdentity(jobRecordatorio(sesionId))
                    .usingJobData(RecordatorioResumenJob.PARAM_SESION_ID, sesionId.toString())
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(key)
                    .startAt(Date.from(Instant.now().plus(RECORDATORIO_24HS)))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withMisfireHandlingInstructionIgnoreMisfires())
                    .build();
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            // Un recordatorio perdido no rompe la disponibilidad del resumen.
            log.warn("No se pudo programar el recordatorio del resumen de la sesion {}",
                    sesionId, e);
        }
    }

    /** Envio del recordatorio (lo invoca {@code RecordatorioResumenJob}). Unico por la
     *  bandera {@code recordatorio_pendiente}: la segunda ejecucion es no-op. */
    @Transactional
    public void enviarRecordatorio(UUID sesionId) {
        ResumenSesion fila = resumenRepo.findBySesionId(sesionId).orElse(null);
        if (fila == null || !fila.isRecordatorioPendiente()) {
            return;
        }
        fila.setRecordatorioPendiente(false);
        resumenRepo.save(fila);
        if (ResumenSesion.ESTADO_GENERADO.equals(fila.getEstado())) {
            log.info("RECORDATORIO_RESUMEN sesionId={} — el resumen esta disponible (T-M6-07).",
                    sesionId);
        }
    }

    // ---------------------------------------------------------------- Quartz

    /**
     * Programa (o re-programa) el job de generacion al instante dado — usado
     * tanto para la primera generacion como para cada reintento del backoff.
     * Idempotente: re-programar no deja disparos viejos colgando (mismo patron
     * que {@code LiberacionEscrowService#programarLiberacion}).
     */
    @Transactional
    public void programarReintento(UUID sesionId, Instant disparo) {
        TriggerKey key = triggerReintento(sesionId);
        try {
            if (scheduler.checkExists(key)) {
                scheduler.rescheduleJob(key, trigger(key, disparo));
                return;
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException(
                    "No se pudo consultar el job de resumen de la sesion " + sesionId, e);
        }
        JobDetail detail = JobBuilder.newJob(ReintentoResumenJob.class)
                .withIdentity(jobReintento(sesionId))
                .usingJobData(ReintentoResumenJob.PARAM_SESION_ID, sesionId.toString())
                .storeDurably()
                .build();
        try {
            scheduler.scheduleJob(detail, trigger(key, disparo));
        } catch (SchedulerException e) {
            // Fail-closed: una fila pendiente sin su disparo quedaria muda en silencio.
            throw new IllegalStateException(
                    "No se pudo agendar el job de resumen de la sesion " + sesionId, e);
        }
    }

    /** Quita el job de generacion (exito, suspension, o fin de reintentos). Benigno:
     * un disparo que quede ya seria no-op por los guards de estado. */
    @Transactional
    public void cancelarReintento(UUID sesionId) {
        try {
            scheduler.unscheduleJob(triggerReintento(sesionId));
            scheduler.deleteJob(jobReintento(sesionId));
        } catch (SchedulerException e) {
            // Benigno — ver javadoc del metodo.
        }
    }

    public static TriggerKey triggerReintento(UUID sesionId) {
        return TriggerKey.triggerKey("resumen-generar-trigger-" + sesionId, GRUPO_JOB);
    }

    public static JobKey jobReintento(UUID sesionId) {
        return new JobKey("resumen-generar-job-" + sesionId, GRUPO_JOB);
    }

    public static TriggerKey triggerRecordatorio(UUID sesionId) {
        return TriggerKey.triggerKey("resumen-recordatorio-trigger-" + sesionId, GRUPO_JOB);
    }

    public static JobKey jobRecordatorio(UUID sesionId) {
        return new JobKey("resumen-recordatorio-job-" + sesionId, GRUPO_JOB);
    }

    private static Trigger trigger(TriggerKey key, Instant disparo) {
        return TriggerBuilder.newTrigger()
                .withIdentity(key)
                .startAt(Date.from(disparo))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionIgnoreMisfires())
                .build();
    }

    // ---------------------------------------------------------------- prompt

    /** FR-SUM-003/008: estructura fija + prohibiciones de evaluacion. */
    private static String armarPrompt(String transcriptAnonimizado) {
        return PromptResumen.INSTRUCCIONES
                + "\nTranscript:\n" + transcriptAnonimizado;
    }
}