package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.jobs.SolicitudExpiracionJob;
import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.SolicitudSesion;
import com.tinku.reservas.repository.SolicitudSesionRepository;
import com.tinku.reservas.web.NuevaSolicitudRequest;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Solicitud de Sesión del menor (US-2, FR-RES-021/022; T-M4-03).
 *
 * Reglas de Artículo II aplicadas acá: la genera SOLO la cuenta del menor (nunca
 * su AR por él, nunca el tutor), es liviana — no bloquea horario ni genera cobro
 * — y solo puede pedir un Tutor que su Adulto Responsable tenga autorizado y no
 * marcado no-confiable (FR-RES-021, mismo criterio que FR-MATCH-004).
 */
@Service
public class SolicitudService {

    /** FR-RES-022 — expiración a 48hs (Tabla_Tiempos_Tinku.md, fila de Solicitud). */
    private static final Duration EXPIRACION_SOLICITUD = Duration.ofHours(48);

    private final SolicitudSesionRepository solicitudRepo;
    private final UsuarioRepository usuarioRepo;
    private final AutorizacionTutorRepository autorizacionRepo;
    private final FranjaService franjaService;
    private final Scheduler scheduler;
    private final PoliticaSesionesMenores politicaMenores;

    public SolicitudService(SolicitudSesionRepository solicitudRepo,
                            UsuarioRepository usuarioRepo,
                            AutorizacionTutorRepository autorizacionRepo,
                            FranjaService franjaService,
                            Scheduler scheduler,
                            PoliticaSesionesMenores politicaMenores) {
        this.solicitudRepo = solicitudRepo;
        this.usuarioRepo = usuarioRepo;
        this.autorizacionRepo = autorizacionRepo;
        this.franjaService = franjaService;
        this.scheduler = scheduler;
        this.politicaMenores = politicaMenores;
    }

    @Transactional
    public SolicitudSesion crear(Usuario menorAutenticado, NuevaSolicitudRequest request) {
        if (menorAutenticado.getTipo() != TipoUsuario.MENOR) {
            throw new SoloMenorException("Solo la cuenta del menor genera Solicitudes de Sesión.");
        }
        Usuario menor = menorAutenticado;
        if (menor.getAdultoResponsable() == null) {
            throw new SoloMenorException(
                    "La cuenta del menor no tiene Adulto Responsable asociado (FR-ID-020).");
        }
        // T-TES-10/DT7: piloto sin menores (US-2). Fail-closed (AGENTS §3).
        politicaMenores.validarSesionesHabilitadas();

        Usuario tutor = usuarioRepo.findById(request.tutorId())
                .orElseThrow(TutorNoAutorizadoParaMenorException::new);
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new TutorNoAutorizadoParaMenorException();
        }

        // FR-RES-021: solo Tutores autorizados por el AR de ese menor y no no-confiable.
        if (!autorizacionRepo.findTutorIdsByAdultoResponsableIdAndMenorIdAndNoConfiableFalse(
                menor.getAdultoResponsable().getId(), menor.getId())
                .contains(request.tutorId())) {
            throw new TutorNoAutorizadoParaMenorException();
        }
        // FR-ID-026 (T02): después de la autorización (403), el CAP vigente del Tutor (409).
        politicaMenores.validarClaseConMenor(tutor.getId());

        Instant horario = request.horarioPropuesto();
        if (!horario.isAfter(Instant.now())) {
            throw new HorarioFueraDeFranjaException("El horario propuesto ya pasó.");
        }
        int duracion = request.duracionMinutos();
        if (!FranjaService.duracionValida(duracion)) {
            throw new DuracionMinutosInvalidaException(
                    "La duración tiene que ser de 30 a 180 minutos, en bloques de 30.");
        }
        if (franjaService.franjaQueContiene(tutor.getId(), horario, duracion).isEmpty()) {
            throw new HorarioFueraDeFranjaException(
                    "El horario no entra entero en una franja del tutor o no empieza en un bloque de 30 minutos.");
        }
        if (solicitudRepo.existsByMenorIdAndTutorIdAndHorarioPropuestoAndEstado(
                menor.getId(), tutor.getId(), horario, EstadoSolicitud.PENDIENTE)) {
            throw new SolicitudDuplicadaException();
        }

        SolicitudSesion solicitud = new SolicitudSesion();
        solicitud.setMenor(menor);
        solicitud.setTutor(tutor);
        solicitud.setHorarioPropuesto(horario);
        solicitud.setDuracionMinutos(duracion);
        solicitud.setExpiraAt(Instant.now().plus(EXPIRACION_SOLICITUD));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        SolicitudSesion guardada = solicitudRepo.save(solicitud);

        programarExpiracion(guardada);
        return guardada;
    }

    /** US-3: Solicitudes pendientes de los menores a cargo del AR. */
    @Transactional(readOnly = true)
    public List<SolicitudSesion> pendientesDelAdultoResponsable(Usuario adultoResponsable) {
        exigirAdultoResponsable(adultoResponsable);
        return solicitudRepo.findByAdultoResponsableIdAndEstado(adultoResponsable.getId(),
                EstadoSolicitud.PENDIENTE);
    }

    /** Recuperación del job (FR-RES-022): expira pendientes cuyo expira_at ya pasó. Idempotente. */
    @Transactional
    public int expirarVencidas() {
        List<SolicitudSesion> vencidas = solicitudRepo.findByEstadoAndExpiraAtBefore(
                EstadoSolicitud.PENDIENTE, Instant.now());
        vencidas.forEach(s -> s.setEstado(EstadoSolicitud.EXPIRADA));
        if (!vencidas.isEmpty()) {
            solicitudRepo.saveAll(vencidas);
        }
        return vencidas.size();
    }

    /** Job puntual de Quartz, programado a {@code expira_at} (persistido, sobrevive reinicios). */
    void programarExpiracion(SolicitudSesion solicitud) {
        JobDetail detail = JobBuilder.newJob(SolicitudExpiracionJob.class)
                .withIdentity("expirar-solicitud-" + solicitud.getId(), "m4-reservas")
                .usingJobData(SolicitudExpiracionJob.PARAM_SOLICITUD_ID, solicitud.getId().toString())
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("expirar-solicitud-trigger-" + solicitud.getId(), "m4-reservas")
                .startAt(java.util.Date.from(solicitud.getExpiraAt()))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionIgnoreMisfires())
                .build();
        try {
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            // La transacción comitea igual: si no se programa el job, la
            // recuperación por barrido (expirarVencidas) cubre la expiración.
            throw new IllegalStateException("No se pudo programar la expiración de la Solicitud.", e);
        }
    }

    private void exigirAdultoResponsable(Usuario usuario) {
        if (usuario.getTipo() == TipoUsuario.MENOR || !usuario.isCapacidadAdultoResponsable()) {
            throw new SoloAdultoResponsableException(
                    "Solo una cuenta con capacidad Adulto Responsable puede revisar Solicitudes.");
        }
    }
}