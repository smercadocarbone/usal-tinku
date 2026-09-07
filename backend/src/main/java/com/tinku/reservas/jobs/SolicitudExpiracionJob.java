package com.tinku.reservas.jobs;

import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.SolicitudSesion;
import com.tinku.reservas.repository.SolicitudSesionRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz (JobStore persistido, Artículo IV/X) que expira una Solicitud
 * de Sesión a las 48hs si sigue pendiente (FR-RES-022, T-M4-03). Se programa por
 * cada Solicitud en {@code expira_at}; idempotente: si la Solicitud ya dejó de
 * estar pendiente (convertida o rechazada), no hace nada.
 */
@Component
@DisallowConcurrentExecution
public class SolicitudExpiracionJob implements Job {

    public static final String PARAM_SOLICITUD_ID = "solicitudId";

    private final SolicitudSesionRepository solicitudRepo;

    public SolicitudExpiracionJob(SolicitudSesionRepository solicitudRepo) {
        this.solicitudRepo = solicitudRepo;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID solicitudId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_SOLICITUD_ID));
        solicitudRepo.findById(solicitudId).ifPresent(this::expirarSiPendiente);
    }

    private void expirarSiPendiente(SolicitudSesion solicitud) {
        if (solicitud.getEstado() == EstadoSolicitud.PENDIENTE) {
            solicitud.setEstado(EstadoSolicitud.EXPIRADA);
            solicitudRepo.save(solicitud);
        }
    }
}