# R-D — Agenda y franjas: baja de franjas, franjas contiguas, horarios y perfil público

**Tarea:** T-REM-D · **Rama:** `fix/rem-d-agenda` · **Depende de:** nada · **Decisiones:** D-1 (baja de franjas), D-2 (franjas contiguas)
Fuente: `hallazgos_parciales/T1.md` (2, 3, 8, 9, 12), `T2.md` (H1, H2, H3, H10), `T7.md` (3).

Estado del código verificado (2026-10-15):
- `FranjaController` (`backend/src/main/java/com/tinku/reservas/web/`) solo tiene `POST /api/tutores/franjas`,
  `GET /{tutorId}/franjas` y `GET /{tutorId}/horarios`. No hay baja.
- `FranjaDisponibilidad` ya tiene el campo `activa` y todas las consultas filtran `activa = true`
  (`findByTutorIdAndActivaTrueOrderByHoraInicio`): una baja lógica no requiere migración.
- Las reservas guardan `horario` y `horarioFin` absolutos: **no dependen de la franja** una vez creadas.
- Quienes validan "cabe en una franja": `ReservaService:591` (crear), `ReservaService:693` (reprogramar) y
  `SolicitudService:101`, los tres con `franjaService.franjaQueContiene(...).isEmpty()`.

## D-1 Baja de franjas (T2-H1) — Dec D-1

**Spec primero:** agregar a `docs/specs/Spec_M4_Reservas_Agenda.md`: en US-1 (publicar franjas) un escenario
"**Dado** que quiero dejar de ofrecer un horario, **cuando** quito una franja, **entonces** deja de poder
reservarse y las clases ya reservadas dentro de ella se mantienen"; y `FR-RES-025 _(agregado)_`: "El Tutor puede
quitar una franja propia. La baja es lógica y no cancela ni mueve reservas existentes". Sin decisión técnica
nueva (no requiere ADR).

**Backend:**
1. `FranjaController`: `@DeleteMapping("/franjas/{id}")` → 200 con `{ "id": ..., "reservasFuturasEnLaFranja": n }`.
2. `FranjaService.quitar(Usuario tutor, UUID franjaId)`:
   - 404 (`FranjaNoEncontradaException`, mapeada en `ReservasExceptionHandler`) si no existe **o no es del
     usuario** (mismo criterio que B-4: no revelar existencia ajena);
   - `franja.setActiva(false)` y `save`;
   - `reservasFuturasEnLaFranja`: reservas del Tutor en estado distinto de `CANCELADA` cuyo `horario` es futuro y
     cae dentro de la franja (reusá `aplicaA(franja, fecha)` y el rango horario). Es solo informativo para la UI.
3. Idempotente: quitar una franja ya inactiva devuelve 404.

**Frontend** (`frontend/src/components/tutor/TabHorarios.tsx`, hoy solo `api.post`): cada fila de "Franjas
publicadas" suma un botón "Quitar" con diálogo de confirmación: "Dejás de ofrecer este horario. Las clases ya
reservadas se mantienen." (si `reservasFuturasEnLaFranja > 0`, agregar "Tenés N clases reservadas en este
horario."). Función `quitarFranja(id)` en `src/lib/api.ts`.

**Tests (RED):**
- Backend `FranjaIntegracionTest`: `quitar_franjaPropia_200_yDejaDeReservarse` (después de quitarla,
  `POST /api/reservas` en ese horario da 422 y `/horarios` no lo lista); `quitar_franjaAjena_404`;
  `quitar_noDejaSinEfectoReservasExistentes` (una reserva `confirmada` en esa franja sigue `confirmada` y
  `reservasFuturasEnLaFranja == 1`); `quitar_doble_404`; `quitar_comoEstudiante_403`.
- Playwright `tests/cuenta/horarios.spec.ts` (API mockeada): el botón "Quitar" abre el diálogo y llama a
  `DELETE /api/tutores/franjas/{id}`.

## D-2 Franjas contiguas, `/horarios` con duraciones largas y reservas (T1-M2, T1-M3) — Dec D-2

**Causa confirmada** (`HorariosDisponiblesService.horariosDelDia`, líneas ~109–121): el bucle
`while (!cursor.plusMinutes(duracionMinutos).isAfter(f.getHoraFin()))` suma minutos a un `LocalTime`, que **da la
vuelta a medianoche**: con una franja 21:00–22:00 y 180 min, `cursor + 180 = 00:00`, que "no es posterior" a
22:00, y el bucle sigue y genera bloques fuera de franja, duplicados y desordenados. `franjaQueContiene`
(`FranjaService:111`) tiene el mismo patrón con `ini.plusMinutes(...)`.
Además, 15 franjas de 1 h seguidas no se combinan, así que un Tutor así nunca ofrece clases de más de 1 h.

**Arreglo:** una sola fuente de verdad de la disponibilidad de un día, sin usar `LocalTime` para sumar.
1. `FranjaService`: método nuevo
   ```java
   /** Rangos [desdeMin, hastaMin) del día en minutos desde medianoche, con las franjas contiguas o
    *  superpuestas unidas. Sin aritmética de LocalTime: no puede dar la vuelta a medianoche. */
   public List<RangoMinutos> rangosUnidos(UUID tutorId, LocalDate fecha)
   ```
   (`record RangoMinutos(int desde, int hasta)`; ordenar por `desde`; unir si `siguiente.desde <= actual.hasta`).
2. `FranjaService.cabeEnDisponibilidad(UUID tutorId, Instant inicio, int duracionMinutos)` → `boolean`:
   `duracionValida`, `inicio` alineado a 30 min **desde el comienzo del rango unido**, y
   `[inicio, inicio+duración)` dentro del rango, todo en minutos (`inicioMin + duración <= rango.hasta`).
3. Reemplazar `franjaQueContiene(...).isEmpty()` por `!cabeEnDisponibilidad(...)` en los 3 llamadores
   (`ReservaService:591`, `:693`, `SolicitudService:101`) y borrar `franjaQueContiene` (los tests de
   `FranjaServiceTest` usan solo `estaDentroDeFranjaActiva`; no se rompen).
4. `HorariosDisponiblesService.horariosDelDia`: iterar sobre `rangosUnidos` con enteros y
   `fecha.atStartOfDay(ZONA).plusMinutes(min)` para obtener el `Instant`. Resultado ordenado y sin duplicados.
   Con esto, 15 franjas de 1 h seguidas ofrecen 30/60/90/120/150/180.
5. **No** cambiar la regla de 30–180 min de **publicar** una franja (FR-RES-024): sigue valiendo para cada franja.
6. El mensaje de `HorarioFueraDeFranjaException` queda igual.

**Tests (RED):** `HorariosDisponiblesServiceTest` y `FranjaServiceTest`:
- franjas 21–22 y 22–23 con `duracionMinutos=180` → lista vacía (el rango unido es de 2 h); con 120 → un bloque
  21:00 y 21:30 y 22:00… hasta que `inicio+120 <= 23:00`, ordenados, sin duplicados y todos dentro del rango.
- 15 franjas de 1 h (08–23): 180 min → inicios de 08:00 a 20:00 cada 30 min.
- Franjas no contiguas (08–09 y 10–11) con 120 → vacío.
- `cabeEnDisponibilidad` en el límite: la reserva que termina exacto a las 23:00 cabe; una que termina 23:30, no.
- Integración: con el seed de María (15 franjas de 1 h), `POST /api/reservas` de 180 min a las 10:00 → 201
  (hoy 422); y a las 21:30 de 180 min → 422.
- **Regresión:** el test existente de `AUD-025` (franjas superpuestas) sigue verde.

## D-3 Franja con fecha pasada y día de la semana inválido (T2-H2, T2-H3)

`PublicarFranjaRequest` (`backend/src/main/java/com/tinku/reservas/web/`): el `@AssertTrue` solo comprueba que
venga uno de los dos campos; el mensaje dice "(0-6)" pero no lo valida, así que `diaSemana=9` llega a la base,
salta el `CHECK` y da 500.
1. `diaSemana`: `@Min(0) @Max(6)` con mensaje "diaSemana tiene que estar entre 0 (domingo) y 6 (sábado)."
2. `fechaEspecifica` pasada: validar en `FranjaService.publicar` (tiene que usar `ReservasZonaHoraria.ZONA`,
   no `LocalDate.now()` del servidor) y lanzar una excepción propia → 422 "La fecha de la franja ya pasó."
3. **Tests (RED):** `diaSemana=9` → 400 (hoy 500); `diaSemana=-1` → 400; `fechaEspecifica=2020-01-01` → 422
   (hoy 201); fecha de hoy con hora ya pasada → 201 (la franja puntual de hoy es válida); fecha de mañana → 201.

## D-4 El perfil público lista franjas que ya no se pueden reservar (T2-H10, T1-8)

`frontend/src/app/tutores/[id]/page.tsx` (línea ~69–82) arma "Disponibilidad esta semana" con
`/api/tutores/{id}/franjas`: muestra horas ya pasadas y no descuenta lo reservado.
1. Backend: `GET /api/tutores/{tutorId}/disponibilidad?dias=7` (default 7, máx 14, tope por la constante de la
   Tabla de Tiempos "Horizonte del próximo horario libre" = 14 días) →
   `[{ "fecha": "2026-10-15", "rangos": [{ "desde": "09:00", "hasta": "13:00" }] }]`, con los rangos unidos
   (D-2), **libres** (sin reservas no canceladas) y posteriores a `ahora + VENTANA_MINIMA`. Implementarlo en
   `HorariosDisponiblesService` reutilizando `rangosUnidos` y el cálculo de ocupación existente.
2. Frontend: la página consume ese endpoint; si no hay rangos, "Todavía no tiene horarios libres esta semana".
3. **Tests (RED):** hoy a las 11:28 ART un Tutor con franja 08–23 devuelve rangos que empiezan después de
   11:58; una reserva confirmada de 14:00–15:00 parte el rango en dos; un día sin franjas no aparece.
   Playwright: el perfil muestra los rangos devueltos y el mensaje vacío.

## D-5 El picker rotula "Ocupado" lo que ya pasó (T1-8)

`frontend/src/app/reservar/page.tsx` (y el componente del selector de horarios): un bloque con
`isAvailable:false` se muestra "Ocupado" y tachado aunque sea pasado o esté dentro de los 30 min de ventana.
Calcular en el cliente: si `inicio < ahora + 30 min` → rótulo "Ya no se puede reservar" (gris, sin tachar);
si no, "Ocupado". Constante de 30 min importada del mismo lugar que usa la validación del front (buscar con
`rg "30 \* 60" frontend/src`). **Test (RED):** Playwright con `Date` fijo (`page.clock`).

## D-6 El detalle de una clase cancelada o finalizada ofrece "Escribir el pedido" (T1-9)

`frontend/src/app/cuenta/reservas/[id]/page.tsx`: mostrar el bloque "¿Qué querés ver en la clase?" solo si la
reserva está `confirmada` y es futura. **Test (RED):** Playwright con la reserva mockeada en `cancelada` y en
`finalizada` → el bloque no existe; en `confirmada` futura → sí.

## D-7 Mensaje de fecha pasada y precio máximo negativo (T1-12)

1. `ReservaService:583`: la validación de ventana mínima usa `Instant.now().plus(VENTANA_MINIMA).isAfter(horario)` y
   devuelve siempre "Faltan menos de 30 minutos". Separar: `horario` ≤ ahora → "Ese horario ya pasó."; si no,
   el mensaje actual. Mismo cambio en `:689` (reprogramar).
2. Precio máximo `-5` en la UI de búsqueda: hoy se le saca el signo y busca con `$5` sin avisar. El campo no
   acepta negativos (`min=1`) y muestra "Ingresá un precio mayor a cero". (Ver también R-E, E-4.)
3. **Tests (RED):** reserva en el pasado → 422 con "ya pasó"; reserva a 20 min → 422 con "menos de 30 minutos".

## D-8 Lista de franjas de 150 filas (T7-3)

`TabHorarios.tsx`: la lista "Franjas publicadas" es una fila por franja. Agrupar por día y unir visualmente las
contiguas ("Lunes · 08:00–23:00"), con el botón "Quitar" por franja dentro de un acordeón. Reutiliza
`rangosUnidos` solo en el front (función pura `unirContiguas(franjas)` en `src/lib/agenda.ts`).
**Test:** función pura con tabla (contiguas, no contiguas, superpuestas); Playwright: 105 franjas del seed
mockeadas se ven como 7 filas.

## Verificación del chunk

- Backend `./mvnw -B test` (JDK 21), frontend `npm run lint && npm run build && npx playwright test`.
- Repetir con un script tipo T1/T2 en un entorno de pruebas: reservar 90/120/180 con María Pérez (`30111222`),
  quitar una franja con un Tutor del seed y comprobar que sus reservas siguen.
- Marcar cerrados: T2-H1, T2-H2, T2-H3, T2-H10, T1-M2, T1-M3, T1-8, T1-9, T1-12 (parte de fecha), T7-3.
- Tildar T-REM-D.

## NO tocar

- La regla de 30–180 min por franja publicada (FR-RES-024) ni la ventana mínima de 30 min.
- La EXCLUDE de la base que impide reservas superpuestas (V30, AUD-009).
