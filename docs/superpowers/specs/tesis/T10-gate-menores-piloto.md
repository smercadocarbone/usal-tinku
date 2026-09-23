# T10 — Piloto sin menores hasta cerrar kill-switch en cliente y CAP (DT7)

**Branch:** `tesis/gate-menores-piloto` · **Riesgo:** bajo · **Bloqueada por:** —

## 1. Problema (verificado al 2026-09-23)

Nada impide hoy reservar una sesión con un Menor, aunque el clasificador del kill-switch no está
integrado en el cliente (T-M3-06) y ningún Tutor tiene verificados antecedentes penales (CAP
retirado, ver T01–T02). La tesis (Cap. 6, riesgos R-05 y R-10) establece que el piloto **no
incluye menores** hasta cerrar ambas cosas.

## 2. Implementación

1. Property nueva `tinku.menores.sesiones-habilitadas` (default **`false`**) leída por un bean
   `PoliticaSesionesMenores` en `reservas` (un solo lugar que decide).
2. Puntos de corte (fail-closed, ante la duda hay menor — A11):
   - `ReservaService`: toda creación de Reserva cuyo beneficiario sea `TipoUsuario.MENOR`
     (directa o por aprobación de Solicitud) → `SesionesConMenoresDeshabilitadasException` → **409**.
   - `SolicitudService`: el Menor no puede crear Solicitudes mientras esté deshabilitado → 409.
3. Frontend: si el backend responde ese 409, mensaje claro ("Las clases para menores se
   habilitan al finalizar el piloto") en `reservar/page.tsx` y en la vista del Menor. No ocultar
   la cuenta del Menor: solo la acción.
4. `AGENTS.md` §3: agregar una línea que diga que la flag solo pasa a `true` con T-M3-06 **y**
   T02 cerradas.

## 3. Tests (RED primero)

1. `crearReserva_beneficiarioMenor_conFlagFalse_409`.
2. `aprobarSolicitud_conFlagFalse_409` (no se crea la Reserva ni el cobro).
3. `crearReserva_beneficiarioAdulto_conFlagFalse_ok` (regresión).
4. `crearReserva_beneficiarioMenor_conFlagTrue_ok` (el flujo existente sigue funcionando).

## 4. Criterios de aceptación

- Suite verde. `E2EFlujoFelizIntegracionTest` (que usa un Menor) corre con la flag en `true` vía
  `@TestPropertySource`: **documentar por qué** en el test, no apagar la flag global.
- T-TES-10 tildada.

## 5. NO tocar

- Alta de Menores y del Adulto Responsable: siguen permitidas (se prepara el terreno).
- El kill-switch y sus ramas.
