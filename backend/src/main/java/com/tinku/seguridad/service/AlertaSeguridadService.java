package com.tinku.seguridad.service;

import com.tinku.seguridad.model.AlertaSeguridad;
import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.seguridad.evento.AlertaResueltaEvent;
import com.tinku.seguridad.AlertaSeguridadNoEncontradaException;
import com.tinku.seguridad.AlertaYaResueltaException;
import com.tinku.seguridad.DescargoInvalidoException;
import com.tinku.seguridad.SancionInvalidaException;
import com.tinku.seguridad.SoloParteInteresadaException;
import com.tinku.seguridad.evento.SancionAplicadaEvent;
import com.tinku.seguridad.jobs.PurgaClipEvidenciaJob;
import com.tinku.seguridad.model.DecisionAlerta;
import com.tinku.seguridad.model.OrigenSancion;
import com.tinku.seguridad.model.Sancion;
import com.tinku.seguridad.model.TipoSancion;
import com.tinku.seguridad.repository.SancionRepository;
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
import java.util.Set;
import java.util.UUID;

/**
 * Track de Alertas de Seguridad del kill-switch (US-2, T-M9-05):
 * <b>completamente separado</b> del de Denuncia estándar (contradicción de
 * plazos 12hs vs 48hs resuelta en Spec_M9, caso borde #11 — acá NO hay
 * {@code en_revision} ni {@code descargo_vence_at}): la suspensión preventiva
 * ya la hizo M3 y el Admin resuelve dentro de la ventana de 12hs sin esperar
 * descargo (FR-SEC-004). El descargo del Tutor se adjunta en cualquier momento
 * como vía de apelación.
 */
@Service
public class AlertaSeguridadService {

    /** BR-KS-02: retención del clip 30 días desde la resolución de la Alerta. */
    private static final Duration RETENCION_CLIP = Duration.ofDays(30);

    private final AlertaSeguridadRepository alertaRepo;
    private final SancionRepository sancionRepo;
    private final UsuarioRepository usuarioRepo;
    private final SesionAprendizajeRepository sesionRepo;
    private final ApplicationEventPublisher events;
    private final Scheduler scheduler;

    public AlertaSeguridadService(AlertaSeguridadRepository alertaRepo,
                                  SancionRepository sancionRepo,
                                  UsuarioRepository usuarioRepo,
                                  SesionAprendizajeRepository sesionRepo,
                                  ApplicationEventPublisher events,
                                  Scheduler scheduler) {
        this.alertaRepo = alertaRepo;
        this.sancionRepo = sancionRepo;
        this.usuarioRepo = usuarioRepo;
        this.sesionRepo = sesionRepo;
        this.events = events;
        this.scheduler = scheduler;
    }

    /**
     * Alertas propias del Tutor detectado (auditoría 2026-09-20): antes no
     * había forma de que él mismo las viera para saber que existían y poder
     * presentar su descargo — la vía de apelación de FR-SEC-004 dependía de
     * un endpoint que nadie podía descubrir.
     */
    public java.util.List<AlertaSeguridad> misAlertas(Usuario usuario) {
        return alertaRepo.findByDetectadoIdOrderByCreatedAtDesc(usuario.getId());
    }

    /**
     * Descargo del Tutor detectado ({@code detectado_id}) como apelación —
     * nunca bloquea la resolución dentro de las 12hs (FR-SEC-004).
     */
    @Transactional
    public AlertaSeguridad descargar(UUID alertaId, Usuario usuario, String texto) {
        AlertaSeguridad alerta = buscar(alertaId);
        if (!alerta.getDetectadoId().equals(usuario.getId())) {
            throw new SoloParteInteresadaException();
        }
        if (texto == null || texto.length() > 300) {
            throw new DescargoInvalidoException();
        }
        alerta.setDescargoTexto(texto);
        alerta.setDescargoRecibidoAt(Instant.now());
        return alertaRepo.save(alerta);
    }

    /**
     * Resuelve la Alerta: {@code reactivar} restablece el matching del Tutor
     * (acusación falsa, caso borde #4) salvo que tenga otra sanción vigente (AUD-013); {@code sancionar} persiste la sanción y
     * publica {@code sancion.aplicada} (misma transacción que revierte la
     * suspensión preventiva de M3). Ambas fijan la retención del clip (BR-KS-02) y
     * publican {@code alerta.resuelta}, que reembolsa el escrow pausado (ADR-M3-02).
     */
    @Transactional
    public AlertaSeguridad resolver(UUID alertaId, UUID adminId, DecisionAlerta decision,
                                    TipoSancion tipoSancion, Integer diasSuspension) {
        AlertaSeguridad alerta = buscar(alertaId);
        if (!AlertaSeguridad.ESTADO_PENDIENTE_REVISION.equals(alerta.getEstado())) {
            throw new AlertaYaResueltaException(alertaId);
        }

        Usuario detectado = usuarioRepo.findById(alerta.getDetectadoId())
                .orElseThrow(() -> new AlertaSeguridadNoEncontradaException(alertaId));

        if (decision == DecisionAlerta.REACTIVAR) {
            alerta.setEstado(AlertaSeguridad.ESTADO_RESUELTA_REACTIVACION);
            // AUD-013: la Alerta era un falso positivo, pero si el detectado tiene OTRA
            // sanción vigente (ej. definitiva por una denuncia), esa manda.
            if (!sancionRepo.existeSancionVigente(detectado.getId(), Instant.now())) {
                detectado.setActivoParaMatching(true);
                detectado.setEstadoCuenta(EstadoCuenta.ACTIVA);
            }
        } else {
            alerta.setEstado(AlertaSeguridad.ESTADO_RESUELTA_BAJA);
            Sancion sancion = registrarSancion(alerta, adminId, detectado.getId(),
                    tipoSancion, diasSuspension);
            events.publishEvent(new SancionAplicadaEvent(this, sancion.getId(),
                    sancion.getUsuarioSancionadoId(), sancion.getTipo(),
                    sancion.getDiasSuspension(), sancion.getVigenteHasta(), sancion.getOrigen()));
        }
        alerta.setClipRetencionHasta(Instant.now().plus(RETENCION_CLIP));
        programarPurgaClip(alerta.getId(), alerta.getClipRetencionHasta());
        usuarioRepo.save(detectado);
        // ADR-M3-02: el kill-switch dejó el escrow en pausa; resolver la Alerta (en
        // cualquier sentido) libera el reembolso total al Estudiante.
        sesionRepo.findById(alerta.getSesionId()).ifPresent(sesion ->
                events.publishEvent(new AlertaResueltaEvent(this, sesion.getReservaId())));
        return alertaRepo.save(alerta);
    }

    /** BR-KS-02 / AUD-021: job one-shot persistido que borra el clip al vencer la retención. */
    private void programarPurgaClip(UUID alertaId, Instant vence) {
        JobDetail detail = JobBuilder.newJob(PurgaClipEvidenciaJob.class)
                .withIdentity(new JobKey("purga-clip-job-" + alertaId, "m9-seguridad"))
                .usingJobData(PurgaClipEvidenciaJob.PARAM_ALERTA_ID, alertaId.toString())
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TriggerKey.triggerKey("purga-clip-trigger-" + alertaId, "m9-seguridad"))
                .startAt(Date.from(vence))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionFireNow())
                .build();
        try {
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            throw new IllegalStateException("No se pudo agendar la purga del clip de la Alerta " + alertaId, e);
        }
    }

    private Sancion registrarSancion(AlertaSeguridad alerta, UUID adminId, UUID detectadoId,
                                     TipoSancion tipo, Integer diasSuspension) {
        if (tipo == null) {
            throw new SancionInvalidaException("se requiere un tipo de sanción");
        }
        Sancion sancion = new Sancion();
        sancion.setUsuarioSancionadoId(detectadoId);
        sancion.setOrigen(OrigenSancion.ALERTA_SEGURIDAD);
        sancion.setAlertaId(alerta.getId());
        sancion.setAdminId(adminId);
        sancion.setTipo(tipo);
        if (tipo == TipoSancion.SUSPENSION_TEMPORAL) {
            if (diasSuspension == null || !DIAS_VALIDOS.contains(diasSuspension)) {
                throw new SancionInvalidaException("suspensión temporal requiere 7, 15 o 30 días");
            }
            sancion.setDiasSuspension(diasSuspension);
            sancion.setVigenteHasta(Instant.now().plus(Duration.ofDays(diasSuspension)));
        }
        return sancionRepo.save(sancion);
    }

    private static final Set<Integer> DIAS_VALIDOS = Set.of(7, 15, 30);

    private AlertaSeguridad buscar(UUID alertaId) {
        return alertaRepo.findById(alertaId)
                .orElseThrow(() -> new AlertaSeguridadNoEncontradaException(alertaId));
    }
}