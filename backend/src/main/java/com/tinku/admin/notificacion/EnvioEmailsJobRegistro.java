package com.tinku.admin.notificacion;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registra UNA vez el barrido de emails en el job store persistido de Quartz: si ya
 * existe (reinicio), no lo duplica. Intervalo en la Tabla de Tiempos. En tests se
 * apaga ({@code tinku.notificaciones.email.barrido-habilitado=false}) y se llama al
 * servicio directo.
 */
@Component
public class EnvioEmailsJobRegistro implements ApplicationRunner {

    static final JobKey JOB = new JobKey("envio-emails-notificaciones", "admin-notificaciones");

    private final Scheduler scheduler;
    private final boolean habilitado;
    private final int intervaloSegundos;

    public EnvioEmailsJobRegistro(Scheduler scheduler,
                                  @Value("${tinku.notificaciones.email.barrido-habilitado:true}") boolean habilitado,
                                  @Value("${tinku.notificaciones.email.intervalo-segundos:60}") int intervaloSegundos) {
        this.scheduler = scheduler;
        this.habilitado = habilitado;
        this.intervaloSegundos = intervaloSegundos;
    }

    @Override
    public void run(ApplicationArguments args) throws SchedulerException {
        if (!habilitado || scheduler.checkExists(JOB)) {
            return;
        }
        JobDetail detail = JobBuilder.newJob(EnvioEmailsJob.class).withIdentity(JOB).storeDurably().build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("envio-emails-notificaciones-trigger", JOB.getGroup())
                .startNow()
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(intervaloSegundos)
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
        scheduler.scheduleJob(detail, trigger);
    }
}
