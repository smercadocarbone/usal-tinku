package com.tinku.seguridad.service;

import com.tinku.admin.AdminModeracionGate;
import com.tinku.aula.SesionService;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.seguridad.evento.DenunciaRegistradaEvent;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.seguridad.evento.DenunciaResueltaEvent;
import com.tinku.seguridad.AutoDenunciaException;
import com.tinku.seguridad.CasoNoEncontradoException;
import com.tinku.seguridad.DenunciaNoEncontradaException;
import com.tinku.seguridad.DenunciaYaResueltaException;
import com.tinku.seguridad.DescargoInvalidoException;
import com.tinku.seguridad.MenorNoDenunciaException;
import com.tinku.seguridad.NoParticipanteDenunciaException;
import com.tinku.seguridad.SancionInvalidaException;
import com.tinku.seguridad.SoloParteInteresadaException;
import com.tinku.seguridad.evento.SancionAplicadaEvent;
import com.tinku.seguridad.jobs.DescargoVencimientoJob;
import com.tinku.seguridad.jobs.SlaResolucionJob;
import com.tinku.seguridad.model.Denuncia;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.model.MotivoDenuncia;
import com.tinku.seguridad.model.OrigenSancion;
import com.tinku.seguridad.model.Sancion;
import com.tinku.seguridad.model.TipoSancion;
import com.tinku.seguridad.repository.DenunciaRepository;
import com.tinku.seguridad.repository.SancionRepository;
import com.tinku.shared.ResolucionDenuncia;
import com.tinku.shared.notificacion.Notificador;
import com.tinku.shared.notificacion.TipoNotificacion;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Track de Denuncia estándar (US-1/US-3/US-4/US-5): presentación (con rechazo
 * a nivel de autorización del menor, FR-SEC-001), descargo de 48hs con SLA de
 * 5 días hábiles (T-M9-03, Jobs {@code DescargoVencimientoJob}/{@code SlaResolucionJob})
 * y resolución (infundada/fundada/escalada). Todo dentro de la MISMA
 * transacción que publica: los eventos {@code denuncia.registrada} /
 * {@code denuncia.resuelta} / {@code sancion.aplicada} corren en listeners
 * síncronos y si uno falla se aborta el caso completo (Plan_M9 §2.5, atómico).
 */
@Service
public class DenunciaService {

    /** Tabla_Tiempos: ventana de descargo — 48hs desde que el caso entra en revisión. */
    static final Duration PLAZO_DESCARGO = Duration.ofHours(48);

    /** Grupo de jobs de M9 en el JOB_STORE (patrón de LiberacionEscrowService). */
    public static final String GRUPO_JOB = "m9-seguridad";

    private final DenunciaRepository denunciaRepo;
    private final SancionRepository sancionRepo;
    private final UsuarioRepository usuarioRepo;
    private final SesionAprendizajeRepository sesionRepo;
    private final SesionService sesionService;
    private final TransaccionRepository transaccionRepo;
    private final DiasHabilesService diasHabiles;
    private final ApplicationEventPublisher events;
    private final Notificador notificador;
    private final Scheduler scheduler;

    public DenunciaService(DenunciaRepository denunciaRepo, SancionRepository sancionRepo,
                           UsuarioRepository usuarioRepo, SesionAprendizajeRepository sesionRepo,
                           SesionService sesionService,
                           TransaccionRepository transaccionRepo, DiasHabilesService diasHabiles,
                           ApplicationEventPublisher events, Scheduler scheduler,
                           Notificador notificador) {
        this.denunciaRepo = denunciaRepo;
        this.sancionRepo = sancionRepo;
        this.usuarioRepo = usuarioRepo;
        this.sesionRepo = sesionRepo;
        this.sesionService = sesionService;
        this.transaccionRepo = transaccionRepo;
        this.diasHabiles = diasHabiles;
        this.events = events;
        this.scheduler = scheduler;
        this.notificador = notificador;
    }

    // ------------------------------------------------------ presentación (US-1)

    /**
     * Registra la Denuncia y arranca su track (FR-SEC-002): entra directo a
     * {@code en_revision} con descargo de 48hs y agenda el job de vencimiento.
     * Si la sesión denunciada tiene escrow activo, publica {@code denuncia.registrada}
     * (FR-SEC-003 — pausa la liberación de ESA sesión, no de otras).
     *
     * <p>AUD-011 / decisión D5: con {@code sesionId}, denunciante y denunciado tienen que
     * ser participantes de esa Sesión (el AR que paga la sesión de su menor lo es). Sin
     * {@code sesionId} (denuncia de perfil) no se exige vínculo: no congela ningún
     * escrow. Nadie se denuncia a sí mismo.</p>
     */
    @Transactional
    public Denuncia presentar(Usuario denunciante, UUID denunciadoId, UUID sesionId,
                              MotivoDenuncia motivo, String evidenciaUrl) {
        if (denunciante.getTipo() == TipoUsuario.MENOR) {
            throw new MenorNoDenunciaException();
        }
        if (denunciante.getId().equals(denunciadoId)) {
            throw new AutoDenunciaException();
        }
        Usuario denunciado = usuarioRepo.findById(denunciadoId)
                .orElseThrow(() -> new CasoNoEncontradoException(
                        "El usuario denunciado no existe: " + denunciadoId));
        UUID reservaId = null;
        if (sesionId != null) {
            reservaId = sesionRepo.findById(sesionId)
                    .map(SesionAprendizaje::getReservaId)
                    .orElseThrow(() -> new CasoNoEncontradoException(
                            "La sesión denunciada no existe: " + sesionId));
            Set<UUID> participantes = sesionService.participantes(sesionId);
            if (!participantes.contains(denunciante.getId())
                    || !participantes.contains(denunciado.getId())) {
                throw new NoParticipanteDenunciaException();
            }
        }

        Denuncia denuncia = new Denuncia();
        denuncia.setDenuncianteId(denunciante.getId());
        denuncia.setDenunciadoId(denunciado.getId());
        denuncia.setSesionId(sesionId);
        denuncia.setMotivo(motivo);
        denuncia.setEvidenciaUrl(evidenciaUrl);
        denuncia.setEstado(EstadoDenuncia.EN_REVISION);
        denuncia.setDescargoVenceAt(Instant.now().plus(PLAZO_DESCARGO));
        denunciaRepo.save(denuncia);
        programar(denuncia.getId(), "descargo", DescargoVencimientoJob.class,
                DescargoVencimientoJob.PARAM_DENUNCIA_ID, denuncia.getDescargoVenceAt());
        avisarDenunciado(denunciado, denuncia);

        if (reservaId != null && tieneEscrowActivo(reservaId)) {
            events.publishEvent(new DenunciaRegistradaEvent(this, reservaId));
        }
        return denuncia;
    }

    /**
     * FASE2-03 / FR-SEC-010: el denunciado se entera de que corre su plazo de descargo.
     * Nada del denunciante (FR-SEC-006). Outbox: en la misma transacción — si la
     * denuncia no se registra, el aviso tampoco. Si el denunciado es un menor, el
     * aviso va a su Adulto Responsable (Artículo II: el menor no gestiona denuncias).
     */
    private void avisarDenunciado(Usuario denunciado, Denuncia denuncia) {
        Usuario destinatario = denunciado.getTipo() == TipoUsuario.MENOR && denunciado.getAdultoResponsable() != null
                ? denunciado.getAdultoResponsable()
                : denunciado;
        notificador.notificar(destinatario.getId(), TipoNotificacion.DENUNCIA_RECIBIDA, Map.of(
                "denunciaId", denuncia.getId().toString(),
                "descargoVenceAt", denuncia.getDescargoVenceAt().toString()));
    }

    private boolean tieneEscrowActivo(UUID reservaId) {
        return transaccionRepo.findByReservaId(reservaId)
                .map(t -> t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW)
                .orElse(false);
    }

    // ---------------------------------------------------------- descargo (US-3)

    /**
     * Descargo del DENUNCIADO (FR-SEC-006: solo la parte interesada; el
     * denunciante permanece anónimo). Disponible en cualquier momento, incluso
     * post-resolución (apelación registrada, US-3) — nunca bloquea la decisión.
     */
    /**
     * Denuncias recibidas por el propio denunciado (auditoría 2026-09-20):
     * antes no había forma de que él mismo las viera — el plazo de
     * {@code descargoVenceAt} (FR-SEC-010) corría en silencio hasta escalar,
     * sin que nadie hubiera tenido la chance real de responder a tiempo.
     */
    public java.util.List<Denuncia> misDenunciasRecibidas(Usuario usuario) {
        return denunciaRepo.findByDenunciadoIdOrderByCreatedAtDesc(usuario.getId());
    }

    @Transactional
    public Denuncia descargar(UUID denunciaId, Usuario usuario, String texto) {
        Denuncia denuncia = buscar(denunciaId);
        if (!denuncia.getDenunciadoId().equals(usuario.getId())) {
            throw new SoloParteInteresadaException();
        }
        if (texto == null || texto.length() > 300) {
            throw new DescargoInvalidoException();
        }
        denuncia.setDescargoTexto(texto);
        denuncia.setDescargoRecibidoAt(Instant.now());
        return denunciaRepo.save(denuncia);
    }

    // -------------------------------------------- jobs de plazos (T-M9-03)

    /** {@code DescargoVencimientoJob}: venció el descargo sin usarse → arranca
     * el SLA de 5 días hábiles (FR-SEC-010) y agenda el job de escalado. */
    @Transactional
    public void alVencerDescargo(UUID denunciaId) {
        Denuncia denuncia = denunciaRepo.findById(denunciaId).orElse(null);
        if (denuncia == null || denuncia.getEstado() != EstadoDenuncia.EN_REVISION) {
            return; // idempotente: ya vencida o resuelta → no-op
        }
        denuncia.setSlaResolucionVenceAt(diasHabiles.sumarDiasHabiles(Instant.now()));
        denunciaRepo.save(denuncia);
        programar(denuncia.getId(), "sla", SlaResolucionJob.class,
                SlaResolucionJob.PARAM_DENUNCIA_ID, denuncia.getSlaResolucionVenceAt());
    }

    /** {@code SlaResolucionJob}: el Admin excedió los 5 días hábiles y el caso
     * sigue en revisión → prioridad alta en la cola. No se auto-resuelve (Plan §2.3). */
    @Transactional
    public void alVencerSla(UUID denunciaId) {
        Denuncia denuncia = denunciaRepo.findById(denunciaId).orElse(null);
        if (denuncia == null || denuncia.getEstado() != EstadoDenuncia.EN_REVISION) {
            return;
        }
        denuncia.setPrioridadAlta(true);
        denunciaRepo.save(denuncia);
    }

    // ----------------------------------------------------- resolución (US-4/5)

    /**
     * Resuelve la Denuncia (solo el Admin de Moderación y Seguridad, gateado en
     * el controller). En una sola transacción: marca la resolución, persiste la
     * sanción si corresponde ({@code escalada} fuerza suspensión definitiva,
     * FR-SEC-009) y publica {@code denuncia.resuelta} + {@code sancion.aplicada}
     * — los efectos en M4/M5 corren en el mismo hilo/transacción (atómico).
     */
    @Transactional
    public Denuncia resolver(UUID denunciaId, UUID adminId, ResolucionDenuncia resolucion,
                             TipoSancion tipoSancion, Integer diasSuspension) {
        Denuncia denuncia = buscar(denunciaId);
        AdminModeracionGate.exigirNoEsParteDelCaso(adminId,
                denuncia.getDenuncianteId(), denuncia.getDenunciadoId());
        if (denuncia.getEstado() != EstadoDenuncia.EN_REVISION) {
            throw new DenunciaYaResueltaException(denunciaId);
        }
        denuncia.setEstado(estadoFinal(resolucion));
        denuncia.setAdminResolutorId(adminId);
        denuncia.setResueltaAt(Instant.now());
        denunciaRepo.save(denuncia);

        Sancion sancion = null;
        if (resolucion != ResolucionDenuncia.INFUNDADA) {
            TipoSancion tipo = resolucion == ResolucionDenuncia.ESCALADA
                    ? TipoSancion.SUSPENSION_DEFINITIVA : tipoSancion;
            sancion = registrarSancion(denuncia, adminId, tipo, diasSuspension);
        }

        events.publishEvent(new DenunciaResueltaEvent(this, denuncia.getId(),
                denuncia.getDenunciadoId(), reservaIdDe(denuncia), resolucion));
        if (sancion != null) {
            events.publishEvent(new SancionAplicadaEvent(this, sancion.getId(),
                    sancion.getUsuarioSancionadoId(), sancion.getTipo(),
                    sancion.getDiasSuspension(), sancion.getVigenteHasta(), sancion.getOrigen()));
        }
        return denuncia;
    }

    private Sancion registrarSancion(Denuncia denuncia, UUID adminId, TipoSancion tipo,
                                     Integer diasSuspension) {
        if (tipo == null) {
            throw new SancionInvalidaException("se requiere un tipo de sanción");
        }
        Sancion sancion = new Sancion();
        sancion.setUsuarioSancionadoId(denuncia.getDenunciadoId());
        sancion.setOrigen(OrigenSancion.DENUNCIA);
        sancion.setDenunciaId(denuncia.getId());
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

    /** Solo 7/15/30 días de suspensión temporal (Plan_M9 §1). */
    private static final Set<Integer> DIAS_VALIDOS = Set.of(7, 15, 30);

    private static EstadoDenuncia estadoFinal(ResolucionDenuncia resolucion) {
        return switch (resolucion) {
            case INFUNDADA -> EstadoDenuncia.RESUELTA_INFUNDADA;
            case FUNDADA -> EstadoDenuncia.RESUELTA_FUNDADA;
            case ESCALADA -> EstadoDenuncia.ESCALADA;
        };
    }

    private UUID reservaIdDe(Denuncia denuncia) {
        if (denuncia.getSesionId() == null) {
            return null;
        }
        return sesionRepo.findById(denuncia.getSesionId())
                .map(SesionAprendizaje::getReservaId).orElse(null);
    }

    private Denuncia buscar(UUID denunciaId) {
        return denunciaRepo.findById(denunciaId)
                .orElseThrow(() -> new DenunciaNoEncontradaException(denunciaId));
    }

    // ------------------------------------------------------------ Quartz

    private void programar(UUID denunciaId, String prefijo, Class<? extends Job> jobClass,
                           String paramNombre, Instant disparo) {
        TriggerKey triggerKey = TriggerKey.triggerKey(prefijo + "-trigger-" + denunciaId, GRUPO_JOB);
        JobDetail detail = JobBuilder.newJob(jobClass)
                .withIdentity(new JobKey(prefijo + "-job-" + denunciaId, GRUPO_JOB))
                .usingJobData(paramNombre, denunciaId.toString())
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startAt(Date.from(disparo))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionIgnoreMisfires())
                .build();
        try {
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            // Fail-closed (patrón LiberacionEscrowService): un plazo de negocio
            // no puede nacer sin su disparo persistido.
            throw new IllegalStateException(
                    "No se pudo agendar el job de plazos de la denuncia: " + denunciaId, e);
        }
    }

    /** Claves de trigger visibles para los tests (patrón SesionService). */
    public static TriggerKey triggerDescargo(UUID denunciaId) {
        return TriggerKey.triggerKey("descargo-trigger-" + denunciaId, GRUPO_JOB);
    }

    public static TriggerKey triggerSla(UUID denunciaId) {
        return TriggerKey.triggerKey("sla-trigger-" + denunciaId, GRUPO_JOB);
    }
}