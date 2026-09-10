package com.tinku.seguridad.jobs;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

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

    public ReactivacionCuentaJob(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID usuarioId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_USUARIO_ID));
        usuarioRepository.findById(usuarioId).ifPresent(this::reactivar);
    }

    private void reactivar(Usuario usuario) {
        // Solo reactiva si sigue SUSPENDIDA: una sanción definitiva posterior
        // no se pisa con el disparo viejo de una temporal (guard idempotente).
        if (usuario.getEstadoCuenta() == EstadoCuenta.SUSPENDIDA) {
            usuario.setEstadoCuenta(EstadoCuenta.ACTIVA);
            usuario.setActivoParaMatching(true);
            usuarioRepository.save(usuario);
        }
    }
}