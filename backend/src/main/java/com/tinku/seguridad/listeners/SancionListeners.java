package com.tinku.seguridad.listeners;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.service.ReservaService;
import com.tinku.seguridad.evento.SancionAplicadaEvent;
import com.tinku.seguridad.jobs.ReactivacionCuentaJob;
import com.tinku.seguridad.model.TipoSancion;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Efectos de {@code sancion.aplicada} (T-M9-06) sobre M1, M2 y M4 — todos en
 * la MISMA transacción que persiste la sanción (el listener corre síncrono en
 * el hilo del publicador): si cualquiera falla, la sanción entera no se aplica
 * (Plan_M9 §2.5, atómico — sin saga ni compensación porque la transacción del
 * publicador aborta ante cualquier listener fallido).
 * <ul>
 *   <li><b>M1:</b> definitiva/baneo → {@code estado_cuenta = suspendida}; temporal
 *       → además un job de Quartz a {@code vigente_hasta} que reactiva (US-6).</li>
 *   <li><b>M2:</b> exclusión del matching. El flag UNICO que leen
 *       {@code UsuarioRepository#tutoresActivosParaMatching} /
 *       {@code #idsActivosParaMatching} es {@code usuarios.activo_para_matching}
 *       (los perfiles de {@code matching.perfiles_tutor_matching} guardan
 *       materias, no navegan el matching) — apagarlo acá cubre M2 sin duplicar
 *       estado, ver comentario en V7.</li>
 *   <li><b>M4:</b> {@code ReservaService#cancelarFuturasPorSancion} — cancela
 *       las reservas futuras (como Tutor o como pagador) y emite
 *       {@code reserva.cancelada} que M5 reembolsa (FR-SEC-008/012).</li>
 * </ul>
 * M5 no tiene listener acá: reacciona a los efectos puntuales vía
 * {@code denuncia.resuelta} (escrow de esa sesión) y a los futuros vía el
 * encadenamiento {@code reserva.cancelada} — ver javadoc del evento.
 */
@Component
public class SancionListeners {

    private static final Logger LOG = LoggerFactory.getLogger(SancionListeners.class);

    private final UsuarioRepository usuarioRepo;
    private final ReservaService reservaService;
    private final Scheduler scheduler;

    public SancionListeners(UsuarioRepository usuarioRepo, ReservaService reservaService,
                            Scheduler scheduler) {
        this.usuarioRepo = usuarioRepo;
        this.reservaService = reservaService;
        this.scheduler = scheduler;
    }

    @EventListener
    @Transactional
    public void onSancionAplicada(SancionAplicadaEvent evento) {
        Usuario sancionado = usuarioRepo.findById(evento.getUsuarioSancionadoId()).orElse(null);
        if (sancionado == null) {
            LOG.warn("Sanción {} a un usuario inexistente ({}): se ignora.",
                    evento.getTipo(), evento.getUsuarioSancionadoId());
            return;
        }
        if (evento.getTipo() == TipoSancion.ADVERTENCIA) {
            LOG.info("Advertencia registrada para el usuario {}", sancionado.getId());
            return; // sin efectos de cuenta ni de reservas (escala FR-SEC-005)
        }

        sancionado.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        sancionado.setActivoParaMatching(false);
        usuarioRepo.save(sancionado);

        if (evento.getTipo() == TipoSancion.SUSPENSION_TEMPORAL) {
            programarReactivacion(sancionado.getId(), evento.getVigenteHasta());
        }
        // M4: idempotente — solo cancela reservas futuras en estados cancelables.
        reservaService.cancelarFuturasPorSancion(evento.getUsuarioSancionadoId());
    }

    /** Job one-shot de Quartz a {@code vigente_hasta} que reactiva la cuenta (US-6). */
    private void programarReactivacion(UUID usuarioId, Instant vigenteHasta) {
        if (vigenteHasta == null) {
            return; // invariable: la validación de suspensión temporal exige vigente_hasta
        }
        TriggerKey triggerKey = TriggerKey.triggerKey("reactivacion-trigger-" + usuarioId, "m9-seguridad");
        JobDetail detail = JobBuilder.newJob(ReactivacionCuentaJob.class)
                .withIdentity(new JobKey("reactivacion-job-" + usuarioId, "m9-seguridad"))
                .usingJobData(ReactivacionCuentaJob.PARAM_USUARIO_ID, usuarioId.toString())
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startAt(Date.from(vigenteHasta))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionIgnoreMisfires())
                .build();
        try {
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            throw new IllegalStateException(
                    "No se pudo agendar la reactivación del usuario " + usuarioId, e);
        }
    }
}