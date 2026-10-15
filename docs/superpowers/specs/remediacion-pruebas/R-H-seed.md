# R-H — Datos del seed de pruebas

**Tarea:** T-REM-H · **Rama:** `fix/rem-h-seed` · **Depende de:** chunk D (franjas contiguas) · Fuente: `hallazgos_parciales/T3.md` (T3-06), `T1.md` (2), `T7.md` (3)

El seed (`seed-demo`) existe para probar la plataforma. Estos arreglos hacen que **el camino feliz de cada rol sea probable** sin tocar la
base a mano. Los runners son `EstudianteSeedRunner`, `TutorSeedRunner` y `HistorialSeedRunner` (perfil `dev` o `seed-demo`), todos
idempotentes: no deben duplicar nada al volver a correr.

## H-1 Los Tutores autorizados para menores tienen CAP (T3-06)

**Qué pasa:** `EstudianteSeedRunner` inserta directo en `identidad.autorizaciones_tutor` a Sofía (`30777888`), María
(`30111222`), Lucas (`31000005`), Lorena (`31000028`) y Natalia (`31000030`) como Tutores autorizados de Mía, Benjamín y Olivia. **El seed
no crea ningún CAP** (el de Lorena que se vio en las pruebas lo aprobó el tester T4 a mano). Resultado: un Adulto Responsable que
intenta **volver a autorizar** a cualquiera de los cinco recibe 409 "todavía no está habilitado para dar clases a menores" (FR-ID-026), y
las reservas de los menores con ellos dan 409 `TUTOR_SIN_CAP`. Con una base sin las pruebas, los 5 darían 409.

**Arreglo:** que el seed deje a los 5 con un CAP `APROBADO` vigente (12 meses desde hoy, Tabla de Tiempos), para que el seed sea
coherente con la regla.
1. En `TutorSeedRunner` (o un `CapSeedRunner` nuevo en el mismo paquete con el mismo `@Profile({"dev","seed-demo"})`), para cada DNI de esa
   lista: si no existe un CAP `APROBADO` vigente del Tutor, insertar uno con `fechaEmision = hoy`, `venceAt = hoy + 12 meses`,
   `estado = APROBADO`, `tieneAntecedentes = false`, `numeroIntento = 1`. **Sin archivo:** el CAP del seed no lleva documento; verificá en
   la tabla (`V6__m1_certificados_antecedentes_penales.sql`; la tabla es `identidad.certificados_antecedentes_penales`, confirmá el nombre con `rg "CREATE TABLE" backend/src/main/resources/db/migration/V6*.sql`) qué columnas son `NOT NULL` y completá solo esas con valores de relleno
   inequívocos (`"seed-demo"`).
2. Respetar el orden de los runners (`@Order`): el CAP tiene que existir **antes** de que se inserten las autorizaciones, o el runner de
   estudiantes inserta las autorizaciones directo en `identidad.autorizaciones_tutor` y no depende de que el CAP exista, así que el orden no
   importa para eso.
3. Documentar en el log de arranque cuántos CAP se crearon.
**Tests (RED):** `SeedDemoIntegracionTest` (ya existe en `backend/src/test/java/com/tinku/seed/`): después del seed, los 5 Tutores
tienen `habilitadoParaMenores = true` y `POST /api/autorizaciones` de Hernán para Benjamín con cada uno → 200/201 (hoy 4 de 5 dan 409).
Idempotencia: correr los runners dos veces no duplica CAP.

## H-2 Franjas del seed más realistas (T1-2, T7-3)

**Qué pasa:** `TutorSeedRunner` (líneas ~49–64 y ~128–150) publica para **cada uno de los 37 Tutores** franjas semanales de **1 hora**, de
08:00 a 23:00 todos los días: **105 franjas por Tutor**. El comentario del runner dice que son de 1 hora "porque la duración de la Sesión ES la
de la franja"; eso quedó viejo (FR-RES-023 enmendada, D6 / AUD-020: la duración la elige quien reserva). Con el chunk D las contiguas se
unen y se pueden reservar clases largas, pero la lista de "Franjas publicadas" tiene 105 filas por Tutor.

**Arreglo:** en `TutorSeedRunner`, publicar franjas de **3 horas** (el máximo permitido, FR-RES-024): 08–11, 11–14, 14–17, 17–20 y 20–23 →
**5 por día, 35 por Tutor**, para **todos** los Tutores del seed. Mantener la cobertura 08–23. Actualizar el comentario del runner.
Es idempotente como hoy: no pisa lo que el Tutor ya haya cargado (`SELECT count(*) … > 0`). Un entorno que ya tiene el seed viejo
conserva sus 105 franjas; el seed nuevo solo aplica a bases nuevas.
**Tests (RED):** `SeedDemoIntegracionTest`: un Tutor del seed (p. ej. `30111222`) tiene 35 franjas activas, todas de 180 minutos; la reserva de 180 min a
las 09:00 → 201; la de 90 min a las 12:30 → 201.

## H-3 El seed y la comisión del resumen en pruebas

No hay cambios. Los pagos del `HistorialSeedRunner` salen marcados `en_bypass` y por eso "Mis cobros" muestra totales en $0: el texto
aclaratorio es del chunk G (G-7), no un cambio de datos.

## H-4 Datos que dejaron las pruebas del 2026-10-14

No son del seed: se limpian con `deploy/demo/limpiar-residuos-pruebas-2026-10-14.sql` (acción **O-6** del plan). Este chunk **no** corre ese script.

## Verificación del chunk

- `./mvnw -B test` (JDK 21); en particular `SeedDemoIntegracionTest` y los de aula/menores que usan el seed.
- Con `docker compose` local y `SPRING_PROFILES_ACTIVE=dev,seed-demo`: levantar, loguear como `40000012` y autorizar a los 5.
- Tildar T-REM-H y marcar T3-06 cerrado.

## NO tocar

- Las contraseñas del seed (`password123`) ni los DNIs: otras pruebas y documentos los citan.
- El runner de admins (`AdminSeedRunner`): solo corre en `dev`.
