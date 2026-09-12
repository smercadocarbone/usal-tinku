-- El job de vencimiento del CAP (T-M1-17) se RETIRÓ junto con la función de CAP
-- (T-M1-15/16): ya no hay certificados que venzan. Pero su JobDetail y Trigger
-- quedaron persistidos en las tablas QRTZ_* de corridas anteriores de la app.
-- Al arrancar, Quartz hace recovery de misfires e intenta cargar la clase
-- com.tinku.identidad.jobs.CapVencimientoJob, que ya no existe ->
-- ClassNotFoundException -> el scheduler no arranca y muere toda la app.
--
-- Esta migración elimina las filas huérfanas. Los DELETE son idempotentes: en
-- una BD nueva (tests con Testcontainers) o ya limpia no borran nada. El scope
-- coincide con la config de application.yml (scheduler-name TinkuScheduler,
-- jobs agrupados por módulo en schema `public`, V3__quartz_tables.sql).

SET search_path TO public;

-- Tablas hijas del trigger (QRTZ_CRON_* / QRTZ_SIMPLE_* / ...): solo tienen
-- TRIGGER_GROUP/TRIGGER_NAME (FK al PK de QRTZ_TRIGGERS), no el job del padre.
DELETE FROM QRTZ_CRON_TRIGGERS
WHERE SCHED_NAME = 'TinkuScheduler' AND TRIGGER_GROUP = 'm1-identidad' AND TRIGGER_NAME = 'capVencimientoTrigger';

DELETE FROM QRTZ_SIMPLE_TRIGGERS
WHERE SCHED_NAME = 'TinkuScheduler' AND TRIGGER_GROUP = 'm1-identidad' AND TRIGGER_NAME = 'capVencimientoTrigger';

DELETE FROM QRTZ_SIMPROP_TRIGGERS
WHERE SCHED_NAME = 'TinkuScheduler' AND TRIGGER_GROUP = 'm1-identidad' AND TRIGGER_NAME = 'capVencimientoTrigger';

DELETE FROM QRTZ_BLOB_TRIGGERS
WHERE SCHED_NAME = 'TinkuScheduler' AND TRIGGER_GROUP = 'm1-identidad' AND TRIGGER_NAME = 'capVencimientoTrigger';

DELETE FROM QRTZ_TRIGGERS
WHERE SCHED_NAME = 'TinkuScheduler' AND JOB_GROUP = 'm1-identidad' AND JOB_NAME = 'capVencimiento';

DELETE FROM QRTZ_JOB_DETAILS
WHERE SCHED_NAME = 'TinkuScheduler' AND JOB_GROUP = 'm1-identidad' AND JOB_NAME = 'capVencimiento';
