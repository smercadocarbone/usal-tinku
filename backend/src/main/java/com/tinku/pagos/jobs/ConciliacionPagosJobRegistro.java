package com.tinku.pagos.jobs;

import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registra UNA vez el barrido de conciliación de pagos (R2) en el job store persistido de
 * Quartz; si ya existe (reinicio), no lo duplica. Frecuencia: Tabla de Tiempos (cada 5 min).
 */
@Component
public class ConciliacionPagosJobRegistro implements ApplicationRunner {

    static final JobKey JOB = new JobKey("conciliacionPagos", "m5-pagos");
    static final int CADA_MINUTOS = 5;

    private final Scheduler scheduler;

    public ConciliacionPagosJobRegistro(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) throws SchedulerException {
        if (scheduler.checkExists(JOB)) {
            return;
        }
        scheduler.scheduleJob(
                JobBuilder.newJob(ConciliacionPagosJob.class).withIdentity(JOB).storeDurably().build(),
                TriggerBuilder.newTrigger()
                        .withIdentity("conciliacionPagosTrigger", JOB.getGroup())
                        .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(CADA_MINUTOS)
                                .withMisfireHandlingInstructionNextWithRemainingCount())
                        .build());
    }
}
