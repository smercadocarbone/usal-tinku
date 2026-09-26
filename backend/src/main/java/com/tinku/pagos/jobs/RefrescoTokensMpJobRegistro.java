package com.tinku.pagos.jobs;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

/**
 * Registra UNA vez el refresco diario de tokens de MercadoPago (ADR-M5-02) en el job store
 * persistido de Quartz; si ya existe (reinicio), no lo duplica. 04:00 de Argentina: horario
 * operativo, no un plazo de negocio.
 */
@Component
public class RefrescoTokensMpJobRegistro implements ApplicationRunner {

    static final JobKey JOB = new JobKey("refrescoTokensMp", "m5-pagos");

    private final Scheduler scheduler;

    public RefrescoTokensMpJobRegistro(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) throws SchedulerException {
        if (scheduler.checkExists(JOB)) {
            return;
        }
        scheduler.scheduleJob(
                JobBuilder.newJob(RefrescoTokensMpJob.class).withIdentity(JOB).storeDurably()
                        .requestRecovery().build(),
                TriggerBuilder.newTrigger()
                        .withIdentity("refrescoTokensMpTrigger", JOB.getGroup())
                        .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(4, 0)
                                .inTimeZone(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build());
    }
}
