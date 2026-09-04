package com.tinku.identidad.jobs;

import com.tinku.identidad.service.CertificadoService;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El job de Quartz (T-M1-17) delega en {@code CertificadoService#marcarVencidos()}
 * (FR-ID-025). La programación/expresión cron y el arranque con JobStore JDBC se
 * verifican con el contexto Spring (Testcontainers) — ver NOTAS_VERIFICACION.md.
 */
class CapVencimientoJobTest {

    @Test
    void ejecutaMarcaVencidos() {
        CertificadoService service = mock(CertificadoService.class);
        CapVencimientoJob job = new CapVencimientoJob(service);

        job.execute(mock(JobExecutionContext.class));

        verify(service).marcarVencidos();
    }
}
