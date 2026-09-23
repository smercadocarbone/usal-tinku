# FASE2-01 — Disponibilidad en bloques de 30 minutos y tarifa por hora (AUD-009 + AUD-020)

**Branch:** sub-branch `aud/fase2-disponibilidad` (desde `aud/fase2-p1-integridad`) ·
**Riesgo: ALTO** · **Findings:** AUD-009, AUD-020 · **Decisión:** D6 ·
**Sub-feature bloqueada por:** **P1** (solo la "duración recomendada del tutor": NO la implementes
salvo que P1 diga que entra al MVP).

> **Cómo ejecutar esta spec.** Es una **receta**: hacé los pasos **en orden**, **uno por commit**, y
> corré el **punto de control** de cada paso antes de pasar al siguiente. Si un punto de control no
> da lo esperado, **PARAR**: no sigas "a ver si se arregla después". Todo lo que está entre comillas
> invertidas (nombres de clases, métodos, columnas) fue verificado contra el código al 2026-09-23;
> si algo no existe con ese nombre, **PARAR** y reportarlo.
>
> Comando de suite (desde `backend/`), el único que cuenta:
> `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test 2>&1 | grep -E "Tests run:.*Failures|BUILD"`
> La línea final tiene que decir `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` y `BUILD SUCCESS`.

---

## 0. Qué problema se resuelve (leelo, no lo saltees)

Dos partes del sistema tratan la **franja de disponibilidad** de forma incompatible:

- `reservas/service/FranjaService.duracionFranjaQueCubre(tutorId, horario)` y
  `aula/SesionService.programarSesion` / `reprogramarSesionProgramada` tratan la franja como **una
  sola sesión**: la sesión dura lo que dura la franja entera.
- `reservas/service/HorariosDisponiblesService.horariosDelDia(tutorId, fecha, duracionMinutos)` la
  trata como **contenedor de bloques**.

Consecuencias:
- **AUD-009:** las exclusiones de V9 `ex_reservas_sin_superposicion_tutor` y
  `ex_reservas_sin_superposicion_beneficiario` comparan `horario WITH =` (**igualdad exacta**). Una
  reserva a las 10:00 y otra a las 10:30 del mismo tutor **no chocan** aunque se superpongan.
- **AUD-020:** la Reserva **no guarda su duración**. Si el tutor borra la franja,
  `programarSesion` tira `IllegalStateException` (500), y el umbral del 50% (FR-AULA-005) cambia de base.

## 1. Modelo nuevo (D6) — reglas que el código TIENE que garantizar

1. Una reserva dura `duracionMinutos` ∈ {30, 60, 90, 120, 150, 180}.
2. `[horario, horario + duracion)` cae **entero** dentro de **una** franja activa del tutor.
3. `horario` está **alineado** a la franja: `(horario − horaInicio de la franja)` es múltiplo de 30 min.
4. Ninguna reserva **no cancelada** del **tutor** ni del **beneficiario** se superpone. Las
   **contiguas** (una termina 11:00 y otra empieza 11:00) son **válidas**.
5. Precio: `precio = precioHora × duracionMinutos / 60`, `BigDecimal`, `scale 2`, `RoundingMode.HALF_UP`,
   **congelado** al crear la Reserva (FR-PAG-013). (Es lo mismo que `precioHora × unidades / 2`.)
6. La reprogramación **conserva** la duración y el precio originales.
7. Ventana mínima de 15 min (FR-RES-013) y timeout de pago (FR-RES-020): **sin cambios**.

## 2. ⚠️ Trampas verificadas (leé las cuatro antes de empezar)

**T1 — Postgres NO acepta `horario + interval` en una EXCLUDE.** La idea obvia
`EXCLUDE USING gist (tutor_id WITH =, tstzrange(horario, horario + make_interval(mins => duracion_minutos)) WITH &&)`
**falla** con `ERROR: functions in index expression must be marked IMMUTABLE`, porque la suma
`timestamptz + interval` (`timestamptz_pl_interval`) es `STABLE`. Verificado en el Postgres del
proyecto. **Solución (probada):** guardar una columna `horario_fin TIMESTAMPTZ` y usar
`tstzrange(horario, horario_fin, '[)')`. Con eso, contiguas entran, superpuestas se rechazan y las
canceladas no cuentan.

**T2 — Los tests existentes reservan a las 15:30 en una franja 15:00–16:00.** Los helpers
`publicarFranjaPuntual` / `publicarFranjaSemanal` de `ReservasFlujosIntegracionTest` crean franjas
**15:00–16:00** y `dentroDeFranja(fecha)` devuelve **15:30**. Si los helpers mandan
`duracionMinutos: 60`, la reserva termina 16:30, **se sale de la franja** y se rompen decenas de
tests. **En los helpers usá `duracionMinutos: 30`** (15:30–16:00 cabe y está alineado).

**T3 — Las solicitudes del menor también necesitan duración.** `ReservaService.aprobarSolicitud`
crea la Reserva desde `SolicitudSesion.getHorarioPropuesto()`. Si la solicitud no guarda duración,
no hay de dónde sacarla. `reservas.solicitudes_sesion` necesita su propia `duracion_minutos`.

**T4 — La pantalla de precio del tutor hoy NO guarda nada.** `frontend/src/components/tutor/TabPrecio.tsx`
manda `PUT /api/pagos/tarifa` con `{ precio_sesion: ... }` (snake_case), pero
`pagos/web/ActualizarTarifaTutorRequest` espera `precioSesion` → **400 siempre** (reproducido).
Este paso lo arregla de paso: el campo pasa a llamarse `precioHora` **en los dos lados**.

## 3. Pasos

### Paso 0 — Preparación (sin commit)
```bash
docker info >/dev/null && echo docker-ok
fd -e sql . backend/src/main/resources/db/migration | sort | tail -3   # anotá el último número (hoy V24)
```
Corré la suite y **anotá N** (el conteo inicial). Todos los pasos siguientes tienen que terminar con
`Failures: 0` y un N **mayor o igual**.

### Paso 1 — Columnas de duración y fin, con backfill
**Archivos:** `backend/src/main/resources/db/migration/V<n>__m4_duracion_reserva.sql` (n = último + 1),
`reservas/model/Reserva.java`, `reservas/model/SolicitudSesion.java`.

**SQL** (copialo tal cual; los comentarios explican cada bloque):
```sql
-- AUD-020: la Reserva guarda su propia duración (D6: bloques de 30 min, 30 a 180).
-- horario_fin existe porque Postgres no acepta horario + interval en una EXCLUDE (STABLE, ver AUD-009).
ALTER TABLE reservas.reservas ADD COLUMN duracion_minutos INT;
ALTER TABLE reservas.reservas ADD COLUMN horario_fin TIMESTAMPTZ;

-- 1) Si la Sesión existe, su duración agendada es la mejor fuente.
UPDATE reservas.reservas r
   SET duracion_minutos = s.duracion_agendada_segundos / 60
  FROM aula.sesiones_aprendizaje s
 WHERE s.reserva_id = r.id AND s.duracion_agendada_segundos IS NOT NULL;

-- 2) Si no, la franja PUNTUAL que cubre el horario (hora argentina).
UPDATE reservas.reservas r
   SET duracion_minutos = EXTRACT(EPOCH FROM (f.hora_fin - f.hora_inicio))::int / 60
  FROM reservas.franjas_disponibilidad f
 WHERE r.duracion_minutos IS NULL
   AND f.tutor_id = r.tutor_id
   AND f.fecha_especifica = (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::date
   AND (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::time >= f.hora_inicio
   AND (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::time <  f.hora_fin;

-- 3) Si no, la franja SEMANAL (dia_semana: 0 = domingo, igual que EXTRACT(DOW)).
UPDATE reservas.reservas r
   SET duracion_minutos = EXTRACT(EPOCH FROM (f.hora_fin - f.hora_inicio))::int / 60
  FROM reservas.franjas_disponibilidad f
 WHERE r.duracion_minutos IS NULL
   AND f.tutor_id = r.tutor_id
   AND f.dia_semana = EXTRACT(DOW FROM (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires'))
   AND (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::time >= f.hora_inicio
   AND (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::time <  f.hora_fin;

-- 4) Normalizar a las reglas de D6: múltiplo de 30, entre 30 y 180. Resto (sin fuente) = 30,
--    que es la duración mínima de la Tabla de Tiempos (única fuente permitida, A3).
UPDATE reservas.reservas
   SET duracion_minutos = GREATEST(30, LEAST(180, (COALESCE(duracion_minutos, 30) / 30) * 30));

UPDATE reservas.reservas SET horario_fin = horario + make_interval(mins => duracion_minutos);

ALTER TABLE reservas.reservas ALTER COLUMN duracion_minutos SET NOT NULL;
ALTER TABLE reservas.reservas ALTER COLUMN horario_fin SET NOT NULL;
ALTER TABLE reservas.reservas ADD CONSTRAINT chk_reservas_duracion
    CHECK (duracion_minutos BETWEEN 30 AND 180 AND duracion_minutos % 30 = 0);
ALTER TABLE reservas.reservas ADD CONSTRAINT chk_reservas_fin_posterior
    CHECK (horario_fin > horario);

-- T3: la Solicitud del menor también lleva su duración (se congela al aprobarla).
ALTER TABLE reservas.solicitudes_sesion ADD COLUMN duracion_minutos INT;
UPDATE reservas.solicitudes_sesion SET duracion_minutos = 30 WHERE duracion_minutos IS NULL;
ALTER TABLE reservas.solicitudes_sesion ALTER COLUMN duracion_minutos SET NOT NULL;
ALTER TABLE reservas.solicitudes_sesion ADD CONSTRAINT chk_solicitudes_duracion
    CHECK (duracion_minutos BETWEEN 30 AND 180 AND duracion_minutos % 30 = 0);
```
**Antes de aplicar en una base con datos:** contá cuántas reservas caen en el paso 4 sin fuente
(`SELECT count(*) FROM reservas.reservas WHERE ...` ejecutando los pasos 1–3 en una transacción que
revertís). Reportá el número en el resumen: si hay muchas, el usuario lo tiene que saber.

**Entidades** (`ddl-auto: validate`: si la columna existe y el campo no, o al revés, el contexto no
levanta):
```java
// Reserva.java
/** D6. Default 30 (la duración mínima de la Tabla de Tiempos): así el código y los tests que hoy
 *  crean reservas con setHorario(...) siguen funcionando sin tocarlos. El flujo real la fija siempre. */
@Column(name = "duracion_minutos", nullable = false)
private Integer duracionMinutos = 30;

@Column(name = "horario_fin", nullable = false)
private Instant horarioFin;

/** horario_fin se deriva SIEMPRE de horario + duracion (AUD-009: la EXCLUDE depende de él). */
@PrePersist
@PreUpdate
void sincronizarHorarioFin() {
    if (horario != null && duracionMinutos != null) {
        this.horarioFin = horario.plus(Duration.ofMinutes(duracionMinutos));
    }
}

/** Forma preferida en código de producción: fija horario y duración juntos. */
public void definirHorario(Instant horario, int duracionMinutos) {
    this.horario = horario;
    this.duracionMinutos = duracionMinutos;
    sincronizarHorarioFin();
}
```
(Imports: `jakarta.persistence.PrePersist`, `jakarta.persistence.PreUpdate`, `java.time.Duration`.)
Y en `SolicitudSesion.java`: `@Column(name = "duracion_minutos", nullable = false) private Integer duracionMinutos = 30;`
(con getter y setter, igual que el resto de la clase).

**Por qué el `@PrePersist`/`@PreUpdate`:** hoy hay **22** llamadas a `setHorario(` sobre una Reserva
(2 en `ReservaService` y 20 en 14 archivos de test). Con el hook, **ninguna se rompe**: no las toques en
este paso. En producción, los pasos 3 y 5 pasan las 2 de `ReservaService` a `definirHorario(...)`.

**Punto de control 1:** suite completa → `Failures: 0`, N igual al inicial.
**Commit:** `feat(reservas): la reserva guarda su duracion y su fin (AUD-020, paso 1)`.

### Paso 2 — La exclusión por rango (AUD-009) · test RED primero
**Test RED** (en `ReservasFlujosIntegracionTest`, usando `escenarioAdulto()` y un helper nuevo):
```java
/** Franja PUNTUAL {fecha} 10:00-12:00 (4 bloques de 30'). */
private void publicarFranja10a12(String tokenTutor, LocalDate fecha) throws Exception {
    mockMvc.perform(post("/api/tutores/franjas")
                    .header("Authorization", "Bearer " + tokenTutor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "fechaEspecifica", fecha.toString(), "horaInicio", "10:00", "horaFin", "12:00"))))
            .andExpect(status().isCreated());
}

@Test
void aud009_reservasSuperpuestasDelMismoTutor_laSegundaDa409() throws Exception {
    EscenarioAdulto e = escenarioAdulto();
    LocalDate dia = e.fecha().plusDays(1);
    publicarFranja10a12(e.tokenTutor(), dia);
    Instant diez = ZonedDateTime.of(dia, LocalTime.of(10, 0), ReservasZonaHoraria.ZONA).toInstant();
    Instant diezYMedia = diez.plus(Duration.ofMinutes(30));
    String otroEstudiante = registrarAdultoYToken(dniUnico(), "Ana", "Paz", true, false);

    postReserva(e.tokenEstudiante(), e.tutorId(), diez, 60).andExpect(status().isCreated());
    // 10:30-11:30 pisa a 10:00-11:00 del mismo tutor → hoy entra (igualdad exacta); tiene que dar 409.
    postReserva(otroEstudiante, e.tutorId(), diezYMedia, 60).andExpect(status().isConflict());
}
```
(`postReserva` es un helper nuevo como `crearReservaDirecta`, que devuelve el `ResultActions` sin
`andExpect`, y manda `duracionMinutos`. Este test necesita el Paso 3 para poder mandar la duración:
si preferís, escribí el test ahora y marcalo como la primera cosa a hacer pasar en el Paso 3. **Lo
obligatorio es haber visto el rojo antes de la migración de este paso.**)

**Migración** `V<n+1>__m4_exclusion_por_rango.sql`:
```sql
-- AUD-009: V9 comparaba horario WITH = (igualdad exacta). Con bloques de 30' las reservas contiguas
-- son el caso normal y hay que distinguirlas del solapamiento real: rango semiabierto [inicio, fin).
ALTER TABLE reservas.reservas DROP CONSTRAINT ex_reservas_sin_superposicion_tutor;
ALTER TABLE reservas.reservas DROP CONSTRAINT ex_reservas_sin_superposicion_beneficiario;

ALTER TABLE reservas.reservas ADD CONSTRAINT ex_reservas_rango_tutor
    EXCLUDE USING gist (tutor_id WITH =, tstzrange(horario, horario_fin, '[)') WITH &&)
    WHERE (estado <> 'cancelada');
ALTER TABLE reservas.reservas ADD CONSTRAINT ex_reservas_rango_beneficiario
    EXCLUDE USING gist (beneficiario_id WITH =, tstzrange(horario, horario_fin, '[)') WITH &&)
    WHERE (estado <> 'cancelada');
```
`btree_gist` ya existe (V9). **Si la migración falla porque hay datos que se superponen**, **PARAR** y
reportar las filas (`SELECT` con `tstzrange(...) &&` entre pares del mismo tutor): no borres reservas.

`reservas/web/ReservasExceptionHandler.handleSuperposicion` sigue traduciendo a 409 (no lo toques
acá: afinar qué constraint se violó es FASE3-02).

**Punto de control 2:** el test del Paso 2 pasa (después del Paso 3) y la suite queda verde.
**Commit:** `fix(reservas): exclusion por rango de tiempo en vez de igualdad exacta (AUD-009)`.

### Paso 3 — Crear reservas y solicitudes con duración (reglas 1, 2, 3 y 5)
1. **Requests** — sumar `@NotNull Integer duracionMinutos` a `reservas/web/NuevaReservaDirectaRequest`
   y `reservas/web/NuevaSolicitudRequest`. **No** a `ReprogramarReservaRequest` (regla 6).
2. **`FranjaService`** — método nuevo que reemplaza el chequeo "solo el inicio" por "todo el rango":
   ```java
   /** Franja activa que contiene ENTERO [inicio, inicio+duracion) con inicio alineado a 30' (D6). */
   public Optional<FranjaDisponibilidad> franjaQueContiene(UUID tutorId, Instant inicio, int duracionMinutos) {
       if (duracionMinutos < 30 || duracionMinutos > 180 || duracionMinutos % 30 != 0) {
           return Optional.empty();
       }
       return franjaQueCubre(tutorId, inicio).filter(f -> {
           LocalTime ini = inicio.atZone(ReservasZonaHoraria.ZONA).toLocalTime();
           long desdeInicio = Duration.between(f.getHoraInicio(), ini).toMinutes();
           LocalTime fin = ini.plusMinutes(duracionMinutos);
           boolean alineado = desdeInicio % 30 == 0;
           boolean cabe = !fin.isAfter(f.getHoraFin()) && fin.isAfter(ini); // fin.isAfter(ini): no cruza medianoche
           return alineado && cabe;
       });
   }
   ```
3. **`ReservaService`** — `crearReserva(pagador, beneficiario, tutor, horario, duracionMinutos)`:
   - validar duración con `DuracionMinutosInvalidaException` (ya existe y **ya** está mapeada a 422 en
     `ReservasExceptionHandler`: no hay que tocar el handler);
   - reemplazar `franjaService.estaDentroDeFranjaActiva(...)` por `franjaQueContiene(...)`: si está
     vacío → `HorarioFueraDeFranjaException` (ya existe, ya da 422) con el mensaje "El horario no
     entra entero en una franja del tutor o no empieza en un bloque de 30 minutos.";
   - `reserva.definirHorario(horario, duracionMinutos)`;
   - precio: `tarifaProveedor.precioHora(tutor.getId()).multiply(BigDecimal.valueOf(duracionMinutos)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)` (el puerto se renombra en el Paso 6; hasta entonces usá `tarifaPorSesion` y dejá un `// TODO paso 6`).
   - `crearDirecta` pasa `request.duracionMinutos()`; `aprobarSolicitud` pasa `solicitud.getDuracionMinutos()`.
4. **`SolicitudService.crear`** — validar con `franjaQueContiene(tutor.getId(), horario, request.duracionMinutos())`
   (reemplaza `estaDentroDeFranjaActiva`) y guardar `solicitud.setDuracionMinutos(...)`.
5. **`reprogramar` / `validarNuevoHorario`** — validar el nuevo horario con
   `franjaQueContiene(tutorId, nuevoHorario, reserva.getDuracionMinutos())` y fijarlo con
   `reserva.definirHorario(nuevoHorario, reserva.getDuracionMinutos())`.
6. **`ReservaResponse`** — sumar `Integer duracionMinutos` y `Instant horarioFin` (al final del record,
   para no romper el orden de los que ya usa el frontend).
7. **Helpers de tests** (**T2**): en todos los tests que arman el JSON de una reserva o solicitud
   (`rg -n "\"horario\"|horarioPropuesto" backend/src/test`: hoy son 6 archivos, 14 usos en
   `ReservasFlujosIntegracionTest`), agregá `"duracionMinutos", 30`. **30**, no 60 (ver T2).

**Tests nuevos** (RED primero, en `ReservasFlujosIntegracionTest`):
- `reservasContiguas_10a11_y_11a12_ambas201` (franja 10–12, dos estudiantes).
- `reservaQueSeSaleDeLaFranja_422` (11:30 de 60 min en franja 10–12).
- `reservaDesalineada_10h15_422`.
- `duracionNoMultiploDe30_422` (45) y `duracionMayorA180_422` (210).
- `beneficiarioConDosReservasSuperpuestasConDistintosTutores_409`.
- `precio_90min_conPrecioHora1000_es1500` y `precio_30min_conPrecioHora999_es499_50`
  (configurá la tarifa del tutor con `PUT /api/pagos/tarifa` antes de reservar, o con el fallback
  `tinku.reservas.tarifa-stub` en un `@TestPropertySource` propio).
- `solicitudDelMenor_conDuracion_alAprobarseLaReservaLaHereda`.
- `reprogramar_conservaLaDuracionYElPrecio`.

**Punto de control 3:** suite verde, N sube por los tests nuevos, y el test del Paso 2 pasa.
**Commit:** `feat(reservas): reservar y solicitar con duracion en bloques de 30 minutos (AUD-009/020)`.

### Paso 4 — La Sesión usa la duración de la Reserva (AUD-020)
En `aula/SesionService`:
- `programarSesion`: reemplazar el bloque `Duration duracionFranja = franjaService.duracionFranjaQueCubre(...).orElseThrow(...)`
  por `Duration duracion = Duration.ofMinutes(reserva.getDuracionMinutos());` y usar `duracion` en el
  job de corte y en `setDuracionAgendadaSegundos`.
- `reprogramarSesionProgramada`: **mismo cambio** (hoy también llama a `duracionFranjaQueCubre`).
- Si `FranjaService.duracionFranjaQueCubre` queda sin usos (`rg -n "duracionFranjaQueCubre" backend/src`),
  borralo.

**Test RED:** `programarSesion_conLaFranjaBorrada_usaLaDuracionDeLaReserva` (crear reserva confirmada,
desactivar/borrar la franja, `programarSesion` → hoy tira `IllegalStateException`; tiene que agendar
el corte en `horario + duracion + 5 min`). Y `corteAntesDel50_seMideContraLaDuracionReservada`
(reserva de 60 min en franja de 120 → el 50% es 30 min).
**Ojo:** cualquier test que hoy afirme "la duración agendada = la de la franja" va a fallar **por diseño**.
Cambialo para que afirme "= la de la reserva" y **decilo en el commit** (A7: el test codificaba el bug).

**Punto de control 4:** suite verde.
**Commit:** `fix(aula): la sesion toma la duracion de su reserva, no de la franja (AUD-020)`.

### Paso 5 — Horarios disponibles en bloques de 30
En `HorariosDisponiblesService.horariosDelDia`:
- validar `duracionMinutos` con la regla 1 (422 si no);
- el cursor avanza **siempre de a 30 min** (`cursor = cursor.plusMinutes(30)`), no de a `duracionMinutos`;
- `seSuperponen` usa `r.getHorario()` y `r.getHorarioFin()` y **deja de** consultar la franja
  (`duracionFranjaQueCubre`). Borrá el parámetro `duracionPorDefecto` si queda sin uso.

**Test:** `horariosDelDia_bloqueDe60_ofreceInicioCada30Minutos` (franja 10–12, duración 60 → inicios
10:00, 10:30, 11:00; no 11:30).
**Commit:** `fix(reservas): horarios disponibles cada 30 minutos contra la duracion real`.

### Paso 6 — Tarifa por hora (y arregla T4)
1. Migración `V<n+2>__m5_tarifa_por_hora.sql`:
   ```sql
   -- D6: la tarifa del tutor pasa a ser POR HORA. El valor numérico NO se convierte: ver advertencia.
   ALTER TABLE pagos.tarifas_tutor RENAME COLUMN precio_sesion TO precio_hora;
   ```
   Si el CHECK de V17 referencia la columna por nombre, Postgres lo renombra solo: verificá con
   `\d pagos.tarifas_tutor` en el contenedor de desarrollo.
2. `pagos/model/TarifaTutor.java`: `precioSesion` → `precioHora` con `@Column(name = "precio_hora", ...)`.
3. `pagos/web/ActualizarTarifaTutorRequest` y `TarifaTutorResponse`: campo `precioHora`, mensajes "por hora".
4. `pagos/service/PagoService.actualizarTarifaTutor(Usuario, BigDecimal precioHora)`.
5. `reservas/port/TarifaProveedor`: `tarifaPorSesion(UUID)` → `precioHora(UUID)`, y
   `pagos/service/TarifaProveedorTutor` igual. Sacá el `// TODO paso 6` del Paso 3.
6. **Frontend** `frontend/src/components/tutor/TabPrecio.tsx`: `PUT /api/pagos/tarifa` con
   `{ precioHora: ... }` (**camelCase**: esto arregla T4) y textos "por hora". Revisá `rg -n "precio_sesion|precioSesion" frontend/src`.
7. `precios_referencia_regional.valor_sugerido`: documentar en su javadoc y en `Spec_M5` que es **por hora**.

**Advertencia de datos (reportala en el resumen, no la resuelvas):** si hay tarifas cargadas
pensando "por sesión", el rename no las convierte. Es una decisión del usuario.

**Tests:** `PagosFlujosIntegracionTest` (usa la tarifa): actualizar al campo nuevo. Test nuevo
`putTarifa_conPrecioHoraCamelCase_200_yLaReservaSeCotizaPorHora`.
**Commit:** `feat(pagos): tarifa por hora y contrato del frontend corregido (D6)`.

### Paso 7 — Frontend mínimo del flujo de reserva
**No rediseñes** (eso es `docs/superpowers/specs/ux/04-descubrir-reservar-pagar.md`). Solo que
funcione con el contrato nuevo:
- `frontend/src/lib/api.ts`: tipos de reserva con `duracionMinutos` y `horarioFin`; tarifa con `precioHora`.
- `frontend/src/app/reservar/page.tsx`: un selector de duración (30, 60, 90… hasta lo que entre en la
  franja elegida), el precio calculado (`precioHora × minutos / 60`) visible antes de confirmar, y
  mandar `duracionMinutos` en el `POST /api/reservas` o en la solicitud del menor.
- Validación: `cd frontend && bun run lint && npx tsc --noEmit -p .`.
**Commit:** `feat(frontend): elegir duracion y ver el precio antes de reservar (D6)`.

### Paso 8 — Documentación (commit propio)
- `REGISTRO_FINDINGS.md`: AUD-009 y AUD-020 → `CERRADO` con los hashes de los pasos 2 y 4.
- `Tasks_Tinku_Implementacion.md` **y** `Tasks_Tinku_Chunks.md`: T-AUD-012.
- `Spec_M4` (FR-RES-007 por rango; duración elegida por el Estudiante; T-M4-12 ya tiene fuente),
  `Spec_M5` (tarifa por hora, FR-PAG-013 sin cambios), `Spec_M3` (la duración agendada sale de la Reserva).
**Commit:** `docs: FASE2-01 cerrada (AUD-009, AUD-020)`.

## 4. Criterio de terminado
- 8 commits, suite verde en cada uno, N final ≥ N inicial + tests nuevos.
- Reporte con: N inicial y final, cuántas reservas cayeron en el backfill sin fuente (Paso 1) y la
  advertencia de tarifas (Paso 6).

## 5. NO tocar
- V9 ni ninguna migración aplicada (A1). La EXCLUDE vieja se **dropea en una migración nueva**.
- El umbral del 50% (FR-AULA-005): cambia la base, no la regla.
- La "duración recomendada" del tutor (P1).
- `ReservasExceptionHandler` (afinar qué constraint se violó es FASE3-02).
