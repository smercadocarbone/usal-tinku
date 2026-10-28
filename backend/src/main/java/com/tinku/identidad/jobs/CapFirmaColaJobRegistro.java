package com.tinku.identidad.jobs;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobPersistenceException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Registra el paso periódico de la cola de firmas del CAP (ADR-M1-10) en el job store
 * persistido. Si ya existe (reinicio), reemplaza el disparador para tomar el intervalo vigente
 * de {@code tinku.cap.validador.intervalo-segundos} (Tabla de Tiempos). Si se pierde un paso,
 * no se recuperan los atrasados: se sigue con el próximo, para no mandar pedidos en ráfaga.
 */
@Component
public class CapFirmaColaJobRegistro implements ApplicationRunner {

    static final JobKey JOB = new JobKey("capFirmaCola", "m1-identidad");
    static final TriggerKey TRIGGER = new TriggerKey("capFirmaColaTrigger", "m1-identidad");

    private final Scheduler scheduler;
    private final int intervaloSegundos;

    public CapFirmaColaJobRegistro(Scheduler scheduler,
                                   @Value("${tinku.cap.validador.intervalo-segundos:30}") int intervaloSegundos) {
        this.scheduler = scheduler;
        this.intervaloSegundos = intervaloSegundos;
    }

    @Override
    public void run(ApplicationArguments args) throws SchedulerException {
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER)
                .forJob(JOB)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(intervaloSegundos)
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
        JobDetail job = JobBuilder.newJob(CapFirmaColaJob.class).withIdentity(JOB).storeDurably().build();
        // Alta o reemplazo en una sola operación del job store (rescheduleJob borra e inserta:
        // dos schedulers sobre la misma base chocaban en qrtz_triggers_pkey). Si otro scheduler
        // lo insertó entre medio, el segundo intento ya lo encuentra y lo actualiza.
        try {
            scheduler.scheduleJob(job, Set.of(trigger), true);
        } catch (JobPersistenceException carrera) {
            scheduler.scheduleJob(job, Set.of(trigger), true);
        }
    }
}
