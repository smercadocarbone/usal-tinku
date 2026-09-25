-- Datos DEMO para probar producción "como si estuviera andando" (runbook §10).
--
-- Crea: 8 Tutores con temas, tarifa, franjas y credencial aprobada; 4 Estudiantes adultos;
-- una familia (Adulto Responsable + menor de 14); 2 Admins (moderación y financiero); y un
-- historial de clases YA DICTADAS (reserva finalizada + sesión + pago liberado sin dinero real +
-- calificaciones de las dos direcciones + resumen) para que perfiles, reputación y paneles
-- tengan contenido. NO crea clases futuras: esas se reservan desde la app (flujo real).
--
-- Todo lo demo usa DNIs 99900xxx; borrar-demo.sql lo quita entero antes del lanzamiento.
-- Nunca se versiona una contraseña: la pasás vos al correrlo.
--
-- Uso (terminal del contenedor `db` en Coolify):
--   psql -U "$POSTGRES_USER" -d tinku
--   \set clave_demo 'UnaClaveLarga-2026'
--   \set email_demo 'tu.casilla@gmail.com'
--   (pegar este archivo entero)
-- email_demo: casilla real con alias "+": cada cuenta queda como tu.casilla+tinku-<alias>@gmail.com,
-- así todos los emails de prueba te llegan a vos y no rebotan contra dominios inexistentes.

\set ON_ERROR_STOP on
\if :{?clave_demo}
\else
  \echo 'Falta la contraseña: \\set clave_demo ''...'' antes de pegar el script.'
  \quit
\endif
\if :{?email_demo}
\else
  \echo 'Falta la casilla: \\set email_demo ''vos@gmail.com'' antes de pegar el script.'
  \quit
\endif

BEGIN;
-- pgcrypto (crypt/gen_salt) quedó en el schema que Flyway tenía por defecto (identidad).
SET LOCAL search_path = public, identidad;
-- \g /dev/null: que la contraseña no quede impresa en la terminal.
SELECT set_config('demo.clave', :'clave_demo', true), set_config('demo.email', :'email_demo', true) \g /dev/null

DO $$
DECLARE
    clave      text := current_setting('demo.clave');
    email_base text := current_setting('demo.email');
    hash       text;
    zona       constant text := 'America/Argentina/Buenos_Aires';
    t          record;
    u          record;
    tutor_ids  uuid[] := '{}';
    est_ids    uuid[] := '{}';
    clases     int[]  := '{}';
    v_tutor    uuid;
    v_est      uuid;
    v_reserva  uuid;
    v_sesion   uuid;
    inicio     timestamptz;
    v_precio   numeric;
    estrellas  int;
    adulto_id  uuid;
    menor_id   uuid;
    admin_uid  uuid;
    i          int;
    k          int;
    comentarios text[] := ARRAY[
        'Explica con mucha paciencia, entendí todo lo que venía arrastrando.',
        'Explica muy claro. Me dejó ejercicios para practicar y me sirvieron un montón.',
        'Clase puntual y bien preparada. Vuelvo seguro.',
        'Me ayudó a llegar al examen con otra confianza.',
        'Muy buena onda y explica con orden.',
        'Fuimos directo a lo que necesitaba, sin vueltas.'];
BEGIN
    IF length(clave) < 10 THEN
        RAISE EXCEPTION 'clave_demo tiene que tener al menos 10 caracteres.';
    END IF;
    IF position('@' in email_base) = 0 THEN
        RAISE EXCEPTION 'email_demo no parece un email: %', email_base;
    END IF;
    IF EXISTS (SELECT 1 FROM identidad.usuarios WHERE dni LIKE '99900___') THEN
        RAISE EXCEPTION 'Los datos demo ya están cargados. Para recargarlos, correr antes borrar-demo.sql.';
    END IF;

    hash := crypt(clave, gen_salt('bf', 10));

    -- ── Tutores ──────────────────────────────────────────────────────────────────────────
    -- temas: (nivel, año/carrera, materia, cuántos temas de ese trayecto, en su orden).
    -- Pocos temas por Tutor a propósito: el embedding (MiniLM) solo mira ~128 tokens.
    FOR t IN
        SELECT * FROM (VALUES
          (1, '99900001', 'María',  'Pérez',     'maria',  DATE '1995-03-12', 9000::numeric, 6,
           'Profesora de Matemática (UNLP). Hace 8 años que preparo a chicos de secundaria para exámenes y previas: vamos de lo que te trabó a lo que te toman.',
           '[["secundario","4°","Matemática",4],["secundario","5°","Matemática",3],["secundario","3°","Matemática",2]]'),
          (2, '99900002', 'Juan',   'García',    'juan',   DATE '1992-07-01', 8500, 6,
           'Traductor público de inglés. Clases de gramática y conversación para secundaria, con foco en que puedas usarlo, no solo aprobar.',
           '[["secundario","4°","Inglés",4],["secundario","5°","Inglés",3],["primario","4°","Inglés",2]]'),
          (3, '99900003', 'Ana',    'Rodríguez', 'ana',    DATE '1989-11-25', 10000, 6,
           'Licenciada en Física (UBA). Física y Química de secundaria con muchos ejemplos de la vida cotidiana y problemas resueltos paso a paso.',
           '[["secundario","4°","Física",4],["secundario","4°","Química",3],["secundario","5°","Química",2]]'),
          (4, '99900004', 'Carlos', 'López',     'carlos', DATE '1998-02-19', 12000, 6,
           'Ingeniero en Sistemas (UTN). Programación desde cero y Algoritmos para primer año: pensamos juntos el problema antes de escribir código.',
           '[["universitario","Ingeniería en Sistemas de Información","Algoritmos y Estructuras de Datos",5],["universitario","Ingeniería en Sistemas de Información","Matemática Discreta",2],["secundario","4°","Programación",2]]'),
          (5, '99900005', 'Laura',  'Martínez',  'laura',  DATE '1994-09-03', 8000, 6,
           'Profesora de Historia. Te ayudo a entender los procesos (no a memorizar fechas) y a armar buenas respuestas para las pruebas escritas.',
           '[["secundario","4°","Historia",4],["secundario","5°","Historia",3],["secundario","4°","Geografía",2]]'),
          (6, '99900006', 'Diego',  'Fernández', 'diego',  DATE '1991-05-30', 15000, 3,
           'Médico, ayudante de Anatomía. Clases para primer año de Medicina con esquemas y repaso de parciales.',
           '[["universitario","Medicina","Anatomía",5],["universitario","Medicina","Fisiología y Biofísica",3]]'),
          (7, '99900007', 'Sofía',  'Gómez',     'sofia',  DATE '2000-08-15', 7000, 6,
           'Maestra de primaria. Apoyo escolar de Matemática y Lengua para chicos de 3° a 5° grado, con juegos y mucha práctica.',
           '[["primario","3°","Matemática",4],["primario","4°","Matemática",3],["primario","4°","Lengua",3]]'),
          (8, '99900008', 'Paula',  'Sánchez',   'paula',  DATE '1997-01-22', 11000, 0,
           'Estudiante avanzada de Ingeniería Civil. Análisis Matemático I y Álgebra para ingresantes. Soy nueva en Tinku.',
           '[["universitario","Ingeniería Civil","Análisis Matemático I",5],["universitario","Ingeniería Civil","Álgebra y Geometría Analítica",3]]')
        ) AS x(n, dni, nombre, apellido, alias, nacimiento, tarifa, clases, bio, temas)
        ORDER BY n
    LOOP
        INSERT INTO identidad.usuarios (dni, nombre, apellido, fecha_nacimiento, tipo, password_hash,
                                        email, estado_cuenta, activo_para_matching, bio)
        VALUES (t.dni, t.nombre, t.apellido, t.nacimiento, 'TUTOR', hash,
                split_part(email_base, '@', 1) || '+tinku-' || t.alias || '@' || split_part(email_base, '@', 2),
                'ACTIVA', TRUE, t.bio)
        RETURNING id INTO v_tutor;
        tutor_ids := tutor_ids || v_tutor;
        clases := clases || t.clases;

        INSERT INTO identidad.credenciales_academicas (tutor_id, tipo_documento, archivo_url, estado, revisado_at)
        VALUES (v_tutor, 'TITULO', 'demo/sin-archivo', 'APROBADO', now() - interval '60 days');

        INSERT INTO matching.perfiles_tutor_matching (tutor_id, tema_ids, activo_para_matching)
        SELECT v_tutor, coalesce(array_agg(te.id ORDER BY sel.ord, te.orden), '{}'), TRUE
          FROM jsonb_array_elements(t.temas::jsonb) WITH ORDINALITY AS sel(j, ord)
          JOIN matching.trayectos tr
            ON tr.nivel = sel.j->>0 AND tr.anio_o_carrera = sel.j->>1 AND tr.materia = sel.j->>2
          JOIN LATERAL (
                SELECT id, orden FROM matching.temas
                 WHERE trayecto_id = tr.id ORDER BY orden, nombre LIMIT (sel.j->>3)::int
               ) te ON TRUE;
        IF (SELECT cardinality(tema_ids) FROM matching.perfiles_tutor_matching p WHERE p.tutor_id = v_tutor) = 0 THEN
            RAISE EXCEPTION 'El catálogo no tiene los temas del Tutor % (¿cambió V20?).', t.dni;
        END IF;

        INSERT INTO pagos.tarifas_tutor (tutor_id, precio_hora) VALUES (v_tutor, t.tarifa);

        -- Lunes a viernes 17–21 y sábados 10–13 (0 = domingo, como EXTRACT(DOW)).
        INSERT INTO reservas.franjas_disponibilidad (tutor_id, dia_semana, hora_inicio, hora_fin)
        SELECT v_tutor, d, TIME '17:00', TIME '21:00' FROM generate_series(1, 5) d
        UNION ALL SELECT v_tutor, 6, TIME '10:00', TIME '13:00';
    END LOOP;

    -- ── Estudiantes adultos ─────────────────────────────────────────────────────────────
    FOR u IN
        SELECT * FROM (VALUES
          (1, '99900101', 'Lucía',     'Romero',  'lucia',     DATE '2003-04-10'),
          (2, '99900102', 'Martín',    'Acosta',  'martin',    DATE '2001-10-02'),
          (3, '99900103', 'Valentina', 'Herrera', 'valentina', DATE '2005-06-21'),
          (4, '99900104', 'Nicolás',   'Silva',   'nicolas',   DATE '1999-12-08')
        ) AS x(n, dni, nombre, apellido, alias, nacimiento)
        ORDER BY n
    LOOP
        INSERT INTO identidad.usuarios (dni, nombre, apellido, fecha_nacimiento, tipo, capacidad_estudiante,
                                        password_hash, email, estado_cuenta)
        VALUES (u.dni, u.nombre, u.apellido, u.nacimiento, 'ADULTO', TRUE, hash,
                split_part(email_base, '@', 1) || '+tinku-' || u.alias || '@' || split_part(email_base, '@', 2),
                'ACTIVA')
        RETURNING id INTO v_est;
        est_ids := est_ids || v_est;
    END LOOP;

    -- ── Familia: Adulto Responsable + menor (las clases con menores siguen apagadas, T-TES-10) ──
    INSERT INTO identidad.usuarios (dni, nombre, apellido, fecha_nacimiento, tipo, capacidad_adulto_responsable,
                                    password_hash, email, estado_cuenta)
    VALUES ('99900201', 'Roberto', 'Díaz', DATE '1980-02-14', 'ADULTO', TRUE, hash,
            split_part(email_base, '@', 1) || '+tinku-roberto@' || split_part(email_base, '@', 2), 'ACTIVA')
    RETURNING id INTO adulto_id;

    INSERT INTO identidad.usuarios (dni, nombre, apellido, fecha_nacimiento, tipo, capacidad_estudiante,
                                    adulto_responsable_id, password_hash, estado_cuenta)
    VALUES ('99900202', 'Tomás', 'Díaz', (current_date - interval '14 years')::date, 'MENOR', TRUE,
            adulto_id, hash, 'ACTIVA')
    RETURNING id INTO menor_id;

    INSERT INTO identidad.consentimientos_menor (menor_id, adulto_responsable_id, version_texto)
    VALUES (menor_id, adulto_id, 'demo');
    -- Roberto autorizó a María (Matemática) para Tomás.
    INSERT INTO identidad.autorizaciones_tutor (adulto_responsable_id, menor_id, tutor_id)
    VALUES (adulto_id, menor_id, tutor_ids[1]);

    -- ── Admins ──────────────────────────────────────────────────────────────────────────
    FOR u IN
        SELECT * FROM (VALUES
          ('99900301', 'Carla',    'Ibáñez', 'moderacion', 'MODERACION_SEGURIDAD'),
          ('99900302', 'Federico', 'Ruiz',   'finanzas',   'SOPORTE_FINANCIERO')
        ) AS x(dni, nombre, apellido, alias, rol)
    LOOP
        INSERT INTO identidad.usuarios (dni, nombre, apellido, fecha_nacimiento, tipo, capacidad_estudiante,
                                        password_hash, email, estado_cuenta)
        VALUES (u.dni, u.nombre, u.apellido, DATE '1990-01-01', 'ADULTO', TRUE, hash,
                split_part(email_base, '@', 1) || '+tinku-' || u.alias || '@' || split_part(email_base, '@', 2),
                'ACTIVA')
        RETURNING id INTO admin_uid;
        INSERT INTO admin.admins (usuario_id, rol) VALUES (admin_uid, u.rol);
    END LOOP;

    -- ── Historial: clases ya dictadas ───────────────────────────────────────────────────
    -- Cada clase en un día distinto (k*8 + i días atrás, 18 h de Argentina, 60 min), así no
    -- choca con las restricciones de superposición de Tutor ni de Estudiante (V30).
    FOR i IN 1..cardinality(tutor_ids) LOOP
        v_tutor := tutor_ids[i];
        SELECT precio_hora INTO v_precio FROM pagos.tarifas_tutor p WHERE p.tutor_id = tutor_ids[i];
        FOR k IN 1..clases[i] LOOP
            v_est := est_ids[((i + k) % 4) + 1];
            inicio := ((current_date - (k * 8 + i)) + TIME '18:00') AT TIME ZONE zona;
            estrellas := CASE WHEN (i + k) % 5 = 0 THEN 4 ELSE 5 END;

            INSERT INTO reservas.reservas (pagador_id, beneficiario_id, tutor_id, horario, horario_fin,
                                           duracion_minutos, precio, estado, created_at)
            VALUES (v_est, v_est, v_tutor, inicio, inicio + interval '60 minutes', 60, v_precio,
                    'finalizada', inicio - interval '3 days')
            RETURNING id INTO v_reserva;

            INSERT INTO aula.sesiones_aprendizaje (reserva_id, livekit_room_id, estado, inicio_real, fin_real,
                                                   duracion_efectiva_segundos, duracion_agendada_segundos,
                                                   estudiante_joined_at, tutor_joined_at, created_at, updated_at)
            VALUES (v_reserva, 'demo-' || v_reserva, 'finalizada', inicio + interval '1 minute',
                    inicio + interval '60 minutes', 3540, 3600, inicio + interval '1 minute', inicio,
                    inicio - interval '5 minutes', inicio + interval '60 minutes')
            RETURNING id INTO v_sesion;

            -- Pago liberado, marcado en_bypass: no movió dinero real (V22).
            INSERT INTO pagos.transacciones (reserva_id, mp_payment_id, monto_bruto, comision_plataforma,
                                             estado, liberar_at, en_bypass, created_at)
            VALUES (v_reserva, 'demo-' || v_reserva, v_precio, round(v_precio * 0.27, 2), 'liberado',
                    inicio + interval '1 day', TRUE, inicio - interval '3 days');

            -- Las dos direcciones: sin la oculta (tutor_a_estudiante) el Tutor quedaría
            -- bloqueado para nuevas reservas (FR-REP-006).
            INSERT INTO reputacion.calificaciones (sesion_id, autor_id, direccion, estrellas, comentario,
                                                   editable_hasta, created_at)
            VALUES (v_sesion, v_est, 'estudiante_a_tutor', estrellas,
                    comentarios[((i * 7 + k) % array_length(comentarios, 1)) + 1],
                    inicio + interval '49 hours', inicio + interval '1 hour'),
                   (v_sesion, v_tutor, 'tutor_a_estudiante', 5, NULL,
                    inicio + interval '49 hours', inicio + interval '1 hour');

            INSERT INTO resumen.resumenes_sesion (sesion_id, estado, resumen_final, intentos, created_at)
            VALUES (v_sesion, 'generado',
                    'Clase de repaso (datos de demostración). Se trabajaron los temas que el estudiante trajo, '
                    || 'se resolvieron ejercicios guiados y quedaron tres ejercicios para practicar antes de la próxima clase.',
                    1, inicio + interval '70 minutes');
        END LOOP;

        INSERT INTO reputacion.senales_implicitas_tutor (tutor_id, puntualidad_promedio, tasa_recontratacion,
                                                         sesiones_dictadas_total)
        VALUES (v_tutor, 1.0, CASE WHEN i IN (6, 8) THEN 0.0 ELSE 0.5 END, clases[i])
        ON CONFLICT (tutor_id) DO UPDATE
            SET sesiones_dictadas_total = EXCLUDED.sesiones_dictadas_total,
                tasa_recontratacion = EXCLUDED.tasa_recontratacion,
                updated_at = now();
    END LOOP;
END $$;

COMMIT;

\echo ''
\echo 'Datos demo cargados. Falta un paso: calcular los embeddings (runbook §10.1, paso 2).'
SELECT tipo, count(*) AS cuentas FROM identidad.usuarios WHERE dni LIKE '99900___' GROUP BY tipo ORDER BY tipo;
