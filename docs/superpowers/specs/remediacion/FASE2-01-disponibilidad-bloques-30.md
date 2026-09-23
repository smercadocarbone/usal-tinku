# FASE2-01 — Disponibilidad en bloques de 30 minutos y tarifa por hora (AUD-009 + AUD-020)

**Branch:** sub-branch `aud/fase2-disponibilidad` → se mergea a `aud/fase2-p1-integridad` ·
**Riesgo: ALTO** (cambia el modelo de datos de M4, el agendamiento de M3, la tarifa de M5 y el
umbral del 50% que decide reembolso vs. liberación) · **Findings:** AUD-009, AUD-020 ·
**Decisión:** D6 · **Sub-feature bloqueada por:** **P1** (solo la "duración recomendada").

> Hacé esta tarea en **commits chicos**, en el orden de §5, con la suite verde en cada uno.
> Si algún paso no cierra, **PARAR**: no la termines "a medias pero compilando".

## 1. Problema (verificado al 2026-09-22)

**Contradicción raíz:** dos partes del sistema tratan la franja de disponibilidad de forma
incompatible.

- `reservas/service/FranjaService` + `aula/SesionService.programarSesion` tratan la franja como
  **una sola sesión**: la duración agendada sale de `franjaService.duracionFranjaQueCubre(tutorId,
  horario)`, o sea el largo de la franja entera. Una franja 10:00–12:00 = una sesión de 120 min.
- `reservas/service/HorariosDisponiblesService.horariosDelDia(tutorId, fecha, duracionMinutos)` la
  trata como **contenedor de bloques** de `duracionMinutos`.

De ahí salen los dos findings:
- **AUD-009:** la exclusión de V9 (`ex_reservas_sin_superposicion_tutor` y
  `ex_reservas_sin_superposicion_beneficiario`) usa `horario WITH =`, **igualdad exacta**. Una
  reserva a las 10:00 y otra a las 10:30 del mismo Tutor **no chocan**, aunque se superpongan.
- **AUD-020:** la duración de la sesión **no se persiste en la Reserva**; se deriva de la franja. Si
  el Tutor borra o edita la franja, `programarSesion` tira `IllegalStateException` → 500, y el umbral
  del 50% (FR-AULA-005) cambia de base.

## 2. Modelo resultante (D6)

- El átomo de disponibilidad es de **30 minutos**. Una franja 10:00–12:00 = **4 unidades**.
- El **Estudiante** (o el Adulto Responsable) elige **cuántas unidades consecutivas** reserva,
  mínimo 1 (30 min), todas **dentro de una misma franja**.
- FR-RES-024 (franjas de 30 a 180 min) sigue valiendo como límite del **contenedor**; en
  consecuencia, una reserva va de 30 a 180 min.
- **Tarifa por hora:** `TarifaTutor.precioSesion` → `precioHora`.
  `precio = precioHora × unidades / 2`, `BigDecimal` scale 2, `RoundingMode.HALF_UP`, **congelado
  al crear la Reserva** (FR-PAG-013, igual que hoy).
- **PARAR (P1):** "el Tutor puede recomendar una duración" es un campo nuevo que no está en ningún
  Spec. Si P1 no está resuelta como "entra al MVP", **no lo implementes**: el modelo funciona sin él.

## 3. Archivos

- **Nuevas migraciones** (números libres al momento de implementar, NUNCA editar V9 — A1):
  - `V<n>__m4_duracion_reserva.sql` — columna + backfill.
  - `V<n+1>__m4_exclusion_por_rango.sql` — reemplazo de las dos exclusiones.
  - `V<n+2>__m5_tarifa_por_hora.sql` — rename de la columna de tarifa.
- `reservas/model/Reserva.java`, `reservas/service/ReservaService.java` (`crearReserva`, flujo de
  solicitudes del menor, reprogramación), `reservas/service/HorariosDisponiblesService.java`,
  DTOs `reservas/web/NuevaReservaDirectaRequest.java`, `NuevaSolicitudRequest.java`,
  `ReprogramarReservaRequest.java`, `ReservaResponse` (sumar `duracionMinutos`).
- `aula/SesionService.java` (`programarSesion`).
- `reservas/port/TarifaProveedor.java` + `pagos/service/TarifaProveedorTutor.java`,
  `pagos/model/TarifaTutor.java`, `pagos/service/PagoService.actualizarTarifaTutor`,
  `pagos/web/ActualizarTarifaTutorRequest.java`, `pagos/web/TarifaTutorResponse.java`.
- Frontend: `frontend/src/lib/api.ts` (tipos y llamadas), la pantalla de precio del Tutor
  (`rg -n "precioSesion" frontend/src`), y el flujo de reserva (`rg -n "DynamicTimeSlotPicker\|duracionMinutos" frontend/src`).
- Tests: `reservas/.../ReservasFlujosIntegracionTest.java` y los de M3/M5 que rompan.

## 4. Reglas de negocio que el código tiene que garantizar

1. `duracionMinutos` ∈ {30, 60, 90, …, 180}: múltiplo de 30, entre 30 y 180.
2. `[horario, horario + duracion)` cae **entero** dentro de **una** franja activa del Tutor
   (reemplaza el chequeo actual `estaDentroDeFranjaActiva(tutorId, horario)`, que solo mira el
   inicio).
3. `horario` está alineado a un borde de unidad **de esa franja** (`horaInicio + k×30min`).
4. Ninguna reserva no cancelada del **Tutor** ni del **beneficiario** se superpone con el rango.
   Reservas **contiguas** (una termina 11:00, la otra empieza 11:00) son **válidas y normales**.
5. Ventana mínima de 15 min (FR-RES-013) y timeout de pago (FR-RES-020): sin cambios.

## 5. Pasos (un commit por paso, suite verde en cada uno)

**Paso 1 — Columna y backfill.** `ALTER TABLE reservas.reservas ADD COLUMN duracion_minutos INT`.
Backfill, en este orden de preferencia:
1. Si existe la Sesión: `duracion_agendada_segundos / 60` de `aula.sesiones_aprendizaje`.
2. Si no: la duración de la franja **puntual** que la cubre (en SQL; las recurrentes también si es
   factible, si no pasá al punto 3).
3. Resto: **30** (la duración mínima de la Tabla de Tiempos, única fuente permitida por A3). Antes
   de aplicarla, **contá cuántas filas caen acá** (`SELECT count(*) …`) y reportalo: en una base con
   datos reales, que haya filas acá es una señal que el usuario tiene que ver.
Después: `SET NOT NULL` + `CHECK (duracion_minutos BETWEEN 30 AND 180 AND duracion_minutos % 30 = 0)`.
`Reserva.duracionMinutos` en la entidad (migración primero, entidad después: `ddl-auto: validate`).

**Paso 2 — Crear reservas con duración.** Los tres requests suman `@NotNull Integer duracionMinutos`
(la reprogramación **conserva** la duración original, igual que conserva el precio, así que ahí no
va en el request). `crearReserva` valida las reglas 1-3 y congela la duración. Test RED primero.

**Paso 3 — Exclusión por rango (AUD-009).** Migración que hace `DROP` de las dos exclusiones de V9 y
crea:
```sql
ALTER TABLE reservas.reservas ADD CONSTRAINT ex_reservas_rango_tutor
  EXCLUDE USING gist (tutor_id WITH =,
    tstzrange(horario, horario + make_interval(mins => duracion_minutos), '[)') WITH &&)
  WHERE (estado <> 'cancelada');
-- ídem ex_reservas_rango_beneficiario con beneficiario_id
```
`'[)'` es lo que hace que las contiguas **no** choquen. `btree_gist` ya está instalada por V9.
**Ojo:** `reservas/web/ReservasExceptionHandler` traduce la violación a 409 mirando el nombre de la
constraint (o cualquier conflicto de FK, que es AUD-023, FASE 3). Actualizá los nombres que busque.
Si hay datos existentes que se superponen, la migración falla: **PARAR** y reportarlo, no borres
reservas.

**Paso 4 — Sesión (AUD-020).** `SesionService.programarSesion` usa
`Duration.ofMinutes(reserva.getDuracionMinutos())` y deja de llamar a `duracionFranjaQueCubre`. Se
elimina el `IllegalStateException` de "no se encontró la franja". Si `duracionFranjaQueCubre` queda
sin usos, borralo.

**Paso 5 — Horarios disponibles.** `horariosDelDia(tutorId, fecha, duracionMinutos)`: el cursor
avanza **de a 30 min** (no de a `duracionMinutos`), así un bloque de 60 puede empezar en cualquier
unidad. La superposición con reservas existentes usa `r.getDuracionMinutos()` (no la aproximación
por franja del método `seSuperponen`). Validar `duracionMinutos` con la regla 1.

**Paso 6 — Tarifa por hora.** Migración `ALTER TABLE pagos.tarifas_tutor RENAME COLUMN precio_sesion
TO precio_hora` (verificá el nombre real de la tabla y la columna). Renombrá en entidad, DTOs,
servicio y controller. `TarifaProveedor.tarifaPorSesion(tutorId)` → `precioHora(tutorId)`, y el
precio de la Reserva se calcula en `ReservaService` con la fórmula de §2.
`precios_referencia_regional.valor_sugerido` (M5-E) pasa a documentarse **por hora** en su javadoc
y en `Spec_M5`. **Advertencia de datos:** si en la base hay tarifas cargadas pensando en "por
sesión", el rename no las convierte. Reportalo en el resumen: es una decisión del usuario, no tuya.

**Paso 7 — Frontend.** Tipos de `api.ts` (`precioSesion` → `precioHora`, `duracionMinutos` en las
reservas). En el flujo de reserva, el usuario elige la duración (30, 60, 90… hasta lo que permita la
franja) y el precio mostrado es `precioHora × unidades / 2`. **No rediseñes la pantalla:** el
rediseño es de las specs de UX. Solo que funcione con el contrato nuevo.

## 6. Tests obligatorios

1. **El que hoy no existe (RED del Paso 3):** franja 10:00–12:00; reserva A a las 10:00 de 60 min;
   reserva B a las 10:30 de 60 min → **409**. Hoy pasa porque la exclusión compara igualdad.
2. `reservasContiguas_10a11_y_11a12_ambasValidas`.
3. `reservaQueExcedeLaFranja_422` (10:30 de 90 min en una franja que termina 11:30… ajustá a una que
   sí exceda).
4. `reservaDesalineada_10h15_422`.
5. `duracionNoMultiploDe30_422` y `duracionMayorA180_422`.
6. `precio_90minConPrecioHora1000_es1500` y `precio_30minConPrecioHora999_es499_50` (HALF_UP).
7. `programarSesion_conLaFranjaBorrada_usaLaDuracionDeLaReserva_noFalla` (RED del Paso 4).
8. `corteAntesDel50_seMideContraLaDuracionReservada` (sesión de 60 min reservada dentro de una
   franja de 120: el 50% es 30 min, no 60).
9. `beneficiario_noPuedeTenerDosReservasSuperpuestas_conDistintosTutores` → 409.
10. `horariosDelDia_bloqueDe60_ofreceInicioCada30Minutos`.

## 7. Criterios de aceptación

- Suite verde en cada commit; la final sin bajas respecto de la anterior.
- `REGISTRO_FINDINGS.md`: AUD-009 y AUD-020 → `CERRADO`. Tasks: T-AUD-012 en los dos archivos.
- `Spec_M4`: FR-RES-007 (superposición por rango), la duración elegida por el Estudiante y T-M4-12
  (el parámetro `duracionMinutos` ya tiene fuente). `Spec_M5`: tarifa por hora (FR-PAG-013 no
  cambia). `Spec_M3`: la duración agendada sale de la Reserva.

## 8. NO tocar

- V9 ni ninguna migración aplicada (A1).
- El umbral del 50% (FR-AULA-005) en sí: cambia la base de cálculo, no la regla.
- La "duración recomendada" del Tutor, salvo P1 resuelta como "entra al MVP".
