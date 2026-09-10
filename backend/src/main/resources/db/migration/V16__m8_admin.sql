-- M8 — Panel de Administración (Spec_M8, Plan_M8 §1).
-- Reemplaza el allowlist de DNI (`tinku.admin.moderacion.ids`) por una tabla:
-- el gate de `com.tinku.shared` ahora resuelve el rol/usuario desde
-- `admin.admins`; la allowlist queda solo como semilla de dev (CommandLineRunner).
--
-- Roles (Plan §1, enum sin combinación en el MVP): un Admin tiene exactamente
-- un rol. Los CHECK y seeds van en MAYÚSCULAS: los enums se persisten con
-- @Enumerated(EnumType.STRING) (misma convención que V13 — denuncias, V11 —
-- transacciones); los VALORES en minúsculas solo existen en el JSON.

SET search_path TO admin, public;

-- Personal interno de Tinku (Plan §1): una fila por Admin. Vinculada 1:1 a un
-- `usuarios` real — el JWT que ya emite `UsuarioDetailsService` lleva el DNI
-- como principal, y el gate resuelve la fila de acá por ese DNI.
CREATE TABLE admin.admins (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  UUID NOT NULL UNIQUE REFERENCES identidad.usuarios(id),
    rol         VARCHAR(30) NOT NULL CHECK (rol IN ('MODERACION_SEGURIDAD', 'SOPORTE_FINANCIERO')),
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admins_rol ON admin.admins(rol);

-- T-M8-05: enrutamiento de tickets por origen (Plan §3.3) — tabla de config
-- chica que el equipo de operaciones edita sin desplegar código. La semilla
-- son los dos orígenes que hoy generan un "contactar a soporte".
CREATE TABLE admin.mapeo_origen_rol (
    origen_modulo VARCHAR(60) PRIMARY KEY,
    rol_asignado  VARCHAR(30) NOT NULL CHECK (rol_asignado IN ('MODERACION_SEGURIDAD', 'SOPORTE_FINANCIERO'))
);

INSERT INTO admin.mapeo_origen_rol (origen_modulo, rol_asignado) VALUES
    ('M1.credencial_agotada', 'MODERACION_SEGURIDAD'),
    ('M5.pago_fallido',       'SOPORTE_FINANCIERO');

-- US-7/FR-ADM-006: canal "contactar a soporte". `origen_modulo` referencia el
-- mapeo → no se puede crear un ticket cuyo rol no se pueda derivar.
CREATE TABLE admin.tickets_soporte (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID NOT NULL REFERENCES identidad.usuarios(id),
    origen_modulo VARCHAR(60) NOT NULL REFERENCES admin.mapeo_origen_rol(origen_modulo),
    asunto        VARCHAR(200) NOT NULL,
    detalle       VARCHAR(1000) NOT NULL,
    estado        VARCHAR(20) NOT NULL DEFAULT 'ABIERTO'
                  CHECK (estado IN ('ABIERTO', 'EN_PROCESO', 'RESUELTO', 'CERRADO')),
    rol_asignado  VARCHAR(30) NOT NULL CHECK (rol_asignado IN ('MODERACION_SEGURIDAD', 'SOPORTE_FINANCIERO')),
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    resuelto_en   TIMESTAMPTZ
);

CREATE INDEX idx_tickets_rol_estado ON admin.tickets_soporte(rol_asignado, estado);

-- US-6/FR-ADM-005/NFR-SEC-04: auditoría APPEND-ONLY. La regla "no editable ni
-- eliminable desde el panel" no se confía al código: se refuerza a nivel de
-- motor de base de datos.
--
-- Mecanismo: en Postgres el OWNER de una tabla siempre puede UPDATE/DELETE, así
-- que la tabla NO es del rol con el que conecta la app (el que corre Flyway,
-- `DB_USER`). Se la damos a un rol NOLOGIN que ninguna sesión usa
-- (`tinku_auditor_append_only`), y a la app solo le otorgamos SELECT e INSERT
-- explícitos. Sin GRANT de UPDATE/DELETE/TRUNCATE y con `REVOKE ALL FROM PUBLIC`
-- no existe ningún camino del rol de aplicación para modificar una fila.
--
-- Notas operativas:
--  * `CREATE ROLE`/`ALTER OWNER` requieren superuser/creador en el usuario de
--    migración (en dev y en los tests de Testcontainers el usuario Flyway es
--    superuser del contenedor; en prod la migración debe correr con un rol con
--    esos permisos, o el rol debe existir ya — documentado, no improvisado).
--  * Si en el futuro hace falta un ALTER de esta tabla, hay que transferir la
--    propiedad temporalmente y devolverla en la misma migración.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tinku_auditor_append_only') THEN
        CREATE ROLE tinku_auditor_append_only NOLOGIN;
    END IF;
END $$;

CREATE TABLE admin.log_auditoria_admin (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id     UUID NOT NULL REFERENCES admin.admins(id),
    accion       VARCHAR(100) NOT NULL,
    entidad_tipo VARCHAR(60) NOT NULL,
    entidad_id   VARCHAR(64),
    detalle      JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE admin.log_auditoria_admin OWNER TO tinku_auditor_append_only;
REVOKE ALL ON admin.log_auditoria_admin FROM PUBLIC;

-- El rol de la app (session_user = quien corre Flyway, en dev/test su superuser)
-- solo puede leer e insertar. Cualquier intento de UPDATE/DELETE falla en el
-- motor, no en el código.
DO $$
DECLARE
    _app_rol TEXT;
BEGIN
    SELECT session_user INTO _app_rol;
    EXECUTE format('GRANT SELECT, INSERT ON admin.log_auditoria_admin TO %I', _app_rol);
END $$;