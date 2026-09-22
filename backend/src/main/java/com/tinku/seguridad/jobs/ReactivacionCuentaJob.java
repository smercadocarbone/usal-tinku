package com.tinku.seguridad.jobs;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.seguridad.repository.SancionRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * T-M9-06 (US-6): reactivación automática al vencer una suspensión temporal.
 * Lo agendan los listeners de {@code SancionAplicadaEvent} a {@code vigente_hasta}
 * (job de Quartz persistido, Artículo IV/X); idempotente — un disparo duplicado
 * sobre una cuenta que ya se reactivó es inocuo.
 */
@Component
@DisallowConcurrentExecution
public class ReactivacionCuentaJob implements Job {

    public static final String PARAM_USUARIO_ID = "usuarioId";

    private final UsuarioRepository usuarioRepository;
    private final SancionRepository sancionRepository;

    public ReactivacionCuentaJob(UsuarioRepository usuarioRepository,
                                 SancionRepository sancionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.sancionRepository = sancionRepository;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID usuarioId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_USUARIO_ID));
        usuarioRepository.findById(usuarioId).ifPresent(this::reactivar);
    }

    private void reactivar(Usuario usuario) {
        // Solo reactiva si sigue SUSPENDIDA y no queda otra sanción vigente: una
        // definitiva (o una temporal más larga) posterior deja la cuenta también en
        // SUSPENDIDA, así que el estado solo no alcanza para no pisarla (AUD-013).
        if (usuario.getEstadoCuenta() == EstadoCuenta.SUSPENDIDA
                && !sancionRepository.existeSancionVigente(usuario.getId(), Instant.now())) {
            usuario.setEstadoCuenta(EstadoCuenta.ACTIVA);
            usuario.setActivoParaMatching(true);
            usuarioRepository.save(usuario);
        }
    }
}