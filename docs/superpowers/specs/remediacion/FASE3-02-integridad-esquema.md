# FASE3-02 — Integridad del esquema: franjas y 409 honesto (AUD-025, AUD-023)

**Branch:** `aud/fase3-p2-calidad` · **Riesgo:** medio · **Precondición:** FASE2-01 mergeada
(cambia las constraints de reservas y los nombres que busca el handler).

Dos sub-tareas (A y B), **un commit cada una**. La C quedó cancelada por la tesis.

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

## C. ~~Borrar las tablas del CAP retirado (AUD-035)~~ — CANCELADA

**No la ejecutes.** La tesis decidió que el CAP vuelve a ser obligatorio para dictar clases a menores
(DT6, `docs/superpowers/specs/tesis/T01-cap-adr-enmienda.md` y `T02-cap-backend.md`), así que las
tablas de V6 **se vuelven a usar**. AUD-035 lo cierra T02. Si encontrás esta sección antes de que T01
esté mergeado, igual **no** dropees nada.

## Criterios de aceptación

- Suite verde en cada commit. `REGISTRO_FINDINGS.md`: AUD-025 y AUD-023 → `CERRADO` (AUD-035 lo cierra T02 de la tesis).
- `Spec_M4`: FR sobre franjas sin superposición.
