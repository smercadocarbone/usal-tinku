# FASE3-02 — Integridad del esquema: franjas, 409 honesto y tablas huérfanas (AUD-025, AUD-023, AUD-035)

**Branch:** `aud/fase3-p2-calidad` · **Riesgo:** medio · **Precondición:** FASE2-01 mergeada
(cambia las constraints de reservas y los nombres que busca el handler).

Tres sub-tareas, **un commit cada una**, en este orden.

## A. Franjas que se superponen (AUD-025)

**Hoy:** `reservas/service/FranjaService.publicar` no valida que la franja nueva se superponga con
otra del mismo Tutor, y `franjaQueCubre(tutorId, horario)` resuelve la ambigüedad con
`.findFirst()`: con dos franjas superpuestas, cuál "cubre" un horario depende del orden en que vuelvan.

**Qué hacer:**
1. Test RED: publicar dos franjas puntuales superpuestas del mismo Tutor → hoy la segunda da 201;
   tiene que dar **409** (o 422, alineate con lo que devuelva `FranjaService` para otros rechazos).
2. Validación en `publicar` para franjas **puntuales y recurrentes** (una recurrente semanal choca
   con otra recurrente del mismo día de semana y horario, y con una puntual en una fecha que caiga en
   ese día). Si expresar las recurrentes como `EXCLUDE` en SQL es complejo, hacé la validación en el
   servicio **y** agregá la `EXCLUDE` solo para las puntuales (migración nueva). Documentá el hueco
   que quede para recurrentes en el javadoc.
3. `franjaQueCubre`: con la validación, a lo sumo una franja cubre un horario. Reemplazá el
   `findFirst()` sobre la lista por una consulta explícita, o dejá un `assert`/log si aparece más
   de una (datos viejos).
4. **Datos viejos:** antes de agregar la `EXCLUDE`, contá las superposiciones existentes. Si hay,
   **PARAR** y reportar: no borres franjas.

## B. 409 solo para la superposición real (AUD-023)

**Hoy:** `reservas/web/ReservasExceptionHandler.handleSuperposicion` traduce **cualquier**
`DataIntegrityViolationException` a 409 "horario ocupado". Una FK violada, un `NOT NULL`, un CHECK
de duración: todo le llega al usuario como "ese horario ya está tomado".

**Qué hacer:** inspeccioná la causa (`ex.getMostSpecificCause()`, o
`org.hibernate.exception.ConstraintViolationException.getConstraintName()`). Solo las constraints
de superposición de reservas (los nombres que dejó FASE2-01, `ex_reservas_rango_*`) → **409** con
el mensaje actual. El resto → **500** con log `ERROR` (es un bug nuestro, no un conflicto del
usuario). Tests: superposición → 409 (regresión); un CHECK violado forzado → 500.

## C. Borrar las tablas del CAP retirado (AUD-035)

**Hoy:** V6 creó `identidad.certificados_antecedentes_penales` (y lo que haya relacionado: verificá
con `rg -n "CREATE TABLE" backend/src/main/resources/db/migration/V6__*.sql`), y ADR-M1-02 retiró
el CAP del onboarding. Las tablas quedaron huérfanas en la base.

**Qué hacer:** migración nueva `V<n>__m1_drop_tablas_cap.sql` con `DROP TABLE IF EXISTS …` y un
comentario que cite ADR-M1-02. **Nunca editar V6** (A1). Antes: `rg -n "certificados_antecedentes|CertificadoAntecedentes" backend/src` tiene que dar cero usos en código. Si alguna entidad todavía la mapea, `ddl-auto: validate` va a fallar al arrancar: **PARAR** y reportar.

## Criterios de aceptación

- Suite verde en cada commit. `REGISTRO_FINDINGS.md`: AUD-025, AUD-023, AUD-035 → `CERRADO`.
- `Spec_M4`: FR sobre franjas sin superposición.
