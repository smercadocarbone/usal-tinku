package com.tinku.config;

import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.simpl.SimpleJobFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba obligatoria de T-000-03: un job programado DEBE sobrevivir a un
 * reinicio del proceso cuando el JobStore es JDBC.
 *
 * Flujo (simula "programar -> matar el proceso -> reiniciar" sobre la MISMA base):
 *   1. un scheduler con el MISMO config que usa la app (JDBC job store sobre el
 *      DataSource de Spring, tablas QRTZ_* en schema public) programa un job
 *      durable con trigger repeatForever y se confirma que dispara;
 *   2. shutdown de ese scheduler ("matar el proceso");
 *   3. se levanta un scheduler NUEVO sobre la Misma base ("reiniciar");
 *   4. se verifica que el job y el trigger siguen programados (leidos desde
 *      las tablas QRTZ_*) y que vuelven a ejecutarse.
 *
 * Arquitectura: la persistencia NO vive en memoria, vive en las tablas QRTZ_*
 * de PostgreSQL (creadas por Flyway V3__quartz_tables.sql, schema public).
 * Se usa SimpleJobFactory para que el job de prueba no dependa del contexto
 * de Spring; lo importante aca es la persistencia, no la inyeccion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class QuartzPersistenciaTest {

    private static final String SCHED_NAME = "PersistTestScheduler";
    private static final AtomicInteger EJECUCIONES = new AtomicInteger(0);

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DataSource dataSource;

    public static class PruebaJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
            EJECUCIONES.incrementAndGet();
        }
    }

    @Test
    void jobProgramadoYRegistroSobrevivenAlReinicio() throws Exception {
        EJECUCIONES.set(0);

        // 1. Scheduler original: programa el job durable con trigger repeatForever.
        JobDetail detalle = JobBuilder.newJob(PruebaJob.class)
                .withIdentity("jobPrueba", "grupoPrueba")
                .storeDurably(true)
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("triggerPrueba", "grupoPrueba")
                .forJob(detalle)
                .startNow()
                .withSchedule(org.quartz.SimpleScheduleBuilder.repeatSecondlyForever(1))
                .build();

        Scheduler original = schedulerNuevo();
        original.scheduleJob(detalle, trigger);
        original.start();
        try {
            assertTrue(original.checkExists(new JobKey("jobPrueba", "grupoPrueba")));
            esperarHasta(() -> EJECUCIONES.get() >= 1); // se ejecuto estando "vivo"
        } finally {
            // 2. "Matar el proceso".
            original.shutdown(false);
        }

        // 3. "Reiniciar": scheduler nuevo sobre la misma base (mismo instance name).
        int antesDeReinicio = EJECUCIONES.get();
        Scheduler reiniciado = schedulerNuevo();
        reiniciado.start();
        try {
            // 4. Sigue programado tras el reinicio (leido desde la base).
            assertTrue(reiniciado.checkExists(new JobKey("jobPrueba", "grupoPrueba")));
            List<? extends Trigger> triggers = reiniciado.getTriggersOfJob(new JobKey("jobPrueba", "grupoPrueba"));
            assertFalse(triggers.isEmpty());

            // 5. Y vuelve a ejecutarse despues del reinicio.
            esperarHasta(() -> EJECUCIONES.get() > antesDeReinicio);
        } finally {
            reiniciado.shutdown(false);
        }
    }

    private Scheduler schedulerNuevo() throws Exception {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource); // usa el JobStore JDBC (LocalDataSourceJobStore)
        factory.setSchedulerName(SCHED_NAME);
        factory.setAutoStartup(false); // se arranca manualmente con start()
        factory.setJobFactory(new SimpleJobFactory()); // instancia PruebaJob por reflection
        Properties props = new Properties();
        props.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.setProperty("org.quartz.jobStore.driverDelegateClass",
                "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        factory.setQuartzProperties(props);
        factory.afterPropertiesSet();
        return factory.getScheduler();
    }

    private static void esperarHasta(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        while (!condicion.getAsBoolean() && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertTrue(condicion.getAsBoolean(), "No se cumplio la condicion a tiempo");
    }
}
