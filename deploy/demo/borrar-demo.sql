-- Borra TODO lo que tenga que ver con las cuentas demo (DNIs 99900xxx) de cargar-demo.sql,
-- incluido lo que se haya creado probando (reservas, pagos, denuncias, notificaciones...) y sus
-- jobs de Quartz pendientes. Los 2 admins demo se anonimizan en vez de borrarse (ver abajo).
-- Correrlo antes de abrir Tinku a usuarios reales (runbook §10.3).
--
-- Uso (terminal del contenedor `db` en Coolify):
--   psql -U "$POSTGRES_USER" -d tinku
--   (pegar este archivo entero)
--
-- Atención: si un pago de PRUEBA de MercadoPago quedó retenido, borrarlo acá no toca nada en
-- MercadoPago. Con credenciales de prueba no hay dinero real en juego.

\set ON_ERROR_STOP on
BEGIN;
-- pgcrypto (crypt/gen_salt) quedó en el schema que Flyway tenía por defecto (identidad).
SET LOCAL search_path = public, identidad;

CREATE TEMP TABLE demo_usuarios ON COMMIT DROP AS
    SELECT id FROM identidad.usuarios WHERE dni LIKE '99900___';
CREATE TEMP TABLE demo_reservas ON COMMIT DROP AS
    SELECT id FROM reservas.reservas
     WHERE pagador_id IN (SELECT id FROM demo_usuarios)
        OR beneficiario_id IN (SELECT id FROM demo_usuarios)
        OR tutor_id IN (SELECT id FROM demo_usuarios);
CREATE TEMP TABLE demo_solicitudes ON COMMIT DROP AS
    SELECT id FROM reservas.solicitudes_sesion
     WHERE menor_id IN (SELECT id FROM demo_usuarios) OR tutor_id IN (SELECT id FROM demo_usuarios);
CREATE TEMP TABLE demo_sesiones ON COMMIT DROP AS
    SELECT id FROM aula.sesiones_aprendizaje WHERE reserva_id IN (SELECT id FROM demo_reservas);
CREATE TEMP TABLE demo_alertas ON COMMIT DROP AS
    SELECT id FROM aula.alertas_seguridad
     WHERE sesion_id IN (SELECT id FROM demo_sesiones) OR detectado_id IN (SELECT id FROM demo_usuarios);
CREATE TEMP TABLE demo_denuncias ON COMMIT DROP AS
    SELECT id FROM seguridad.denuncias
     WHERE sesion_id IN (SELECT id FROM demo_sesiones)
        OR denunciante_id IN (SELECT id FROM demo_usuarios)
        OR denunciado_id IN (SELECT id FROM demo_usuarios);
CREATE TEMP TABLE demo_transacciones ON COMMIT DROP AS
    SELECT id FROM pagos.transacciones WHERE reserva_id IN (SELECT id FROM demo_reservas);
CREATE TEMP TABLE demo_admins ON COMMIT DROP AS
    SELECT id FROM admin.admins WHERE usuario_id IN (SELECT id FROM demo_usuarios);

-- Jobs de Quartz: su nombre termina en el id de la entidad (timeout-pago-<reserva>,
-- liberacion-job-<transaccion>, ...). Sin esto quedarían disparándose sobre filas borradas.
CREATE TEMP TABLE demo_ids ON COMMIT DROP AS
          SELECT id::text AS id FROM demo_usuarios
    UNION SELECT id::text FROM demo_reservas
    UNION SELECT id::text FROM demo_solicitudes
    UNION SELECT id::text FROM demo_sesiones
    UNION SELECT id::text FROM demo_denuncias
    UNION SELECT id::text FROM demo_transacciones;
CREATE TEMP TABLE demo_jobs ON COMMIT DROP AS
    SELECT sched_name, job_name, job_group FROM qrtz_job_details
     WHERE right(job_name, 36) IN (SELECT id FROM demo_ids);
CREATE TEMP TABLE demo_triggers ON COMMIT DROP AS
    SELECT t.sched_name, t.trigger_name, t.trigger_group FROM qrtz_triggers t
     WHERE (t.sched_name, t.job_name, t.job_group) IN (SELECT * FROM demo_jobs);
DELETE FROM qrtz_simple_triggers  WHERE (sched_name, trigger_name, trigger_group) IN (SELECT * FROM demo_triggers);
DELETE FROM qrtz_cron_triggers    WHERE (sched_name, trigger_name, trigger_group) IN (SELECT * FROM demo_triggers);
DELETE FROM qrtz_simprop_triggers WHERE (sched_name, trigger_name, trigger_group) IN (SELECT * FROM demo_triggers);
DELETE FROM qrtz_blob_triggers    WHERE (sched_name, trigger_name, trigger_group) IN (SELECT * FROM demo_triggers);
DELETE FROM qrtz_triggers         WHERE (sched_name, trigger_name, trigger_group) IN (SELECT * FROM demo_triggers);
DELETE FROM qrtz_job_details      WHERE (sched_name, job_name, job_group) IN (SELECT * FROM demo_jobs);

-- M9 / M3 (seguridad)
DELETE FROM seguridad.sanciones
 WHERE alerta_id IN (SELECT id FROM demo_alertas) OR denuncia_id IN (SELECT id FROM demo_denuncias)
    OR usuario_sancionado_id IN (SELECT id FROM demo_usuarios) OR admin_id IN (SELECT id FROM demo_usuarios);
DELETE FROM aula.confirmaciones_killswitch
 WHERE sesion_id IN (SELECT id FROM demo_sesiones) OR detectado_id IN (SELECT id FROM demo_usuarios);
DELETE FROM aula.alertas_seguridad WHERE id IN (SELECT id FROM demo_alertas);
DELETE FROM seguridad.denuncias    WHERE id IN (SELECT id FROM demo_denuncias);

-- Clases: resumen, calificaciones, pagos, sesión, reserva, solicitud
DELETE FROM resumen.resumenes_sesion WHERE sesion_id IN (SELECT id FROM demo_sesiones);
DELETE FROM reputacion.calificaciones
 WHERE sesion_id IN (SELECT id FROM demo_sesiones) OR autor_id IN (SELECT id FROM demo_usuarios);
DELETE FROM pagos.transacciones        WHERE id IN (SELECT id FROM demo_transacciones);
DELETE FROM aula.sesiones_aprendizaje  WHERE id IN (SELECT id FROM demo_sesiones);
DELETE FROM reservas.reservas          WHERE id IN (SELECT id FROM demo_reservas);
DELETE FROM reservas.solicitudes_sesion WHERE id IN (SELECT id FROM demo_solicitudes);

-- Perfil de Tutor
DELETE FROM reservas.franjas_disponibilidad     WHERE tutor_id IN (SELECT id FROM demo_usuarios);
DELETE FROM pagos.tarifas_tutor                 WHERE tutor_id IN (SELECT id FROM demo_usuarios);
DELETE FROM matching.perfiles_tutor_matching    WHERE tutor_id IN (SELECT id FROM demo_usuarios);
DELETE FROM reputacion.senales_implicitas_tutor WHERE tutor_id IN (SELECT id FROM demo_usuarios);
DELETE FROM identidad.credenciales_academicas   WHERE tutor_id IN (SELECT id FROM demo_usuarios);
DELETE FROM identidad.certificados_antecedentes_penales WHERE tutor_id IN (SELECT id FROM demo_usuarios);
DELETE FROM identidad.intentos_credencial       WHERE tutor_id IN (SELECT id FROM demo_usuarios);

-- Cuenta
DELETE FROM matching.busquedas_guardadas  WHERE usuario_id IN (SELECT id FROM demo_usuarios);
DELETE FROM identidad.autorizaciones_tutor
 WHERE adulto_responsable_id IN (SELECT id FROM demo_usuarios) OR menor_id IN (SELECT id FROM demo_usuarios)
    OR tutor_id IN (SELECT id FROM demo_usuarios);
DELETE FROM identidad.consentimientos_menor
 WHERE menor_id IN (SELECT id FROM demo_usuarios) OR adulto_responsable_id IN (SELECT id FROM demo_usuarios);
DELETE FROM identidad.tokens_reset_password WHERE usuario_id IN (SELECT id FROM demo_usuarios);
DELETE FROM identidad.intentos_ocr          WHERE dni LIKE '99900___';
DELETE FROM admin.notificaciones            WHERE destinatario_id IN (SELECT id FROM demo_usuarios);
DELETE FROM admin.tickets_soporte           WHERE usuario_id IN (SELECT id FROM demo_usuarios);
UPDATE pagos.pasarela_estado SET updated_by = NULL WHERE updated_by IN (SELECT id FROM demo_usuarios);

-- Admins demo: NO se borran. El log de auditoría es append-only a nivel de motor (V16) y
-- referencia a admin.admins, así que esas filas quedan. Se desactivan y la cuenta se anonimiza
-- igual que una baja de la app (ADR-M1-05, UsuarioService#darDeBajaMenor): DNI "BAJA-…",
-- sin email, contraseña aleatoria y tokens invalidados. Queda libre el DNI demo para recargar.
UPDATE admin.admins SET activo = FALSE WHERE id IN (SELECT id FROM demo_admins);
UPDATE identidad.usuarios
   SET dni = 'BAJA-' || substr(replace(id::text, '-', ''), 1, 14),
       nombre = 'Perfil', apellido = 'dado de baja', email = NULL,
       fecha_nacimiento = DATE '1900-01-01',
       password_hash = crypt(gen_random_uuid()::text, gen_salt('bf', 10)),
       estado_cuenta = 'BAJA', credentials_version = credentials_version + 1,
       activo_para_matching = FALSE
 WHERE id IN (SELECT usuario_id FROM admin.admins WHERE id IN (SELECT id FROM demo_admins));

-- El resto se borra. Menores y adultos en la misma sentencia (la FK a adulto_responsable se
-- valida al final de la sentencia).
DELETE FROM identidad.usuarios
 WHERE id IN (SELECT id FROM demo_usuarios)
   AND id NOT IN (SELECT usuario_id FROM admin.admins WHERE id IN (SELECT id FROM demo_admins));

COMMIT;

\echo 'Datos demo borrados.'
SELECT count(*) AS cuentas_demo_restantes FROM identidad.usuarios WHERE dni LIKE '99900___';
