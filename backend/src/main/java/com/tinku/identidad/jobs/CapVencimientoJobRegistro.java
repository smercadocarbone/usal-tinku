package com.tinku.identidad.jobs;

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
 * Registra UNA vez el vencimiento diario del CAP (FR-ID-025, T02) en el job store persistido
 * de Quartz; si ya existe (reinicio), no lo duplica. Corre a las 03:00 de Argentina: es un
 * horario operativo, no un plazo de negocio (el plazo es la vigencia de 12 meses).
 */
@Component
public class CapVencimientoJobRegistro implements ApplicationRunner {

    static final JobKey JOB = new JobKey("capVencimiento", "m1-identidad");

    private final Scheduler scheduler;

    public CapVencimientoJobRegistro(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) throws SchedulerException {
        if (scheduler.checkExists(JOB)) {
            return;
        }
        scheduler.scheduleJob(
                JobBuilder.newJob(CapVencimientoJob.class).withIdentity(JOB).storeDurably()
                        .requestRecovery().build(),
                TriggerBuilder.newTrigger()
                        .withIdentity("capVencimientoTrigger", JOB.getGroup())
                        .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(3, 0)
                                .inTimeZone(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
                                .withMisfireHandlingInstructionFireAndProceed())
                        .build());
    }
}
