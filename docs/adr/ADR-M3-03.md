# ADR-M3-03 — Cierre de la sala de LiveKit en el corte: fail-open con reintento persistido

## Estado

Aceptado (2026-09-22). Implementa la decisión de producto **D4** del plan de remediación
(`docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`, Task 1.4) y cierra **AUD-001**.

## Contexto

`SesionService.cortar()` (kill-switch rama menor, rama adultos confirmada) solo persistía el
estado en la base. La sala de LiveKit seguía viva y los tokens emitidos seguían valiendo hasta su
TTL: la videollamada continuaba aunque Tinku la considerara cortada. `Spec_M3` US-6 exige que "la
sesión se corta para ambos", y el Artículo II exige que la rama con menor corte sin depender de
nadie en tiempo real — mucho menos de que el cliente del propio agresor respete una desconexión
local.

El resto del módulo es **fail-closed**: `programarSiFalta` aborta la confirmación de la Reserva si
Quartz no puede agendar los jobs. Aplicar ese mismo criterio acá (abortar el corte si LiveKit no
contesta) sería el error opuesto.

## Decisión

1. `SesionService.cortar()` llama a `LiveKitService.eliminarSala()` (`DeleteRoom` de la API Twirp,
   con el token de servidor). `DeleteRoom` desconecta a **todos** los participantes y cierra la sala.
2. Si LiveKit falla, **el corte NO se aborta**: se persiste igual el estado (sesión cortada,
   Alerta, evento) y se agenda un job de Quartz persistido (`CerrarSalaJob`) que reintenta el
   cierre con backoff **5min / 15min / 1h** (3 reintentos, fila propia en `Tabla_Tiempos_Tinku.md`).
   Si el agendado también falla, se loguea en `ERROR` y el corte se mantiene: ningún fallo de
   LiveKit, ni un `SchedulerException` de Quartz, revierte el corte en la base.
   **Salvedad:** el JobStore de Quartz comparte la transacción de Postgres del corte. Si falla con
   un error de SQL, Postgres aborta toda la transacción y el corte tampoco se persiste. Es el mismo
   modo de fallo que una caída de la base, en la que el corte no se puede persistir de ninguna
   forma, así que no se agrega una transacción separada para cubrirlo.
3. Si la sala ya no existe (`404` de Twirp), el cierre se considera **exitoso** (idempotente).
4. `SesionService.obtenerToken()` rechaza con 422 toda sesión en estado terminal (`finalizada`,
   `finalizada_anticipada`, `interrumpida`): no se emiten tokens nuevos para una sesión cortada.

### Por qué fail-open acá

LiveKit separa el plano de control (API HTTP) del plano de medios (SFU). La API puede estar caída
mientras la videollamada sigue viva. Con fail-closed, un fallo de la API haría que **la sesión no
se corte en ningún lado**, ni siquiera en la base: sin Alerta, sin suspensión preventiva, sin
evento, y con el menor todavía expuesto. Fail-open lleva el cierre de "nunca" a "apenas LiveKit
conteste", y la protección que depende solo de Tinku (Alerta, suspensión, bloqueo de tokens
nuevos) se aplica siempre.

### Desviación del plan: sin `RemoveParticipant`

El plan listaba `RemoveParticipant` además de `DeleteRoom`. No se implementa, por tres motivos:

- `DeleteRoom` ya desconecta a todos los participantes. `RemoveParticipant` no agrega cobertura.
- `RemoveParticipant` exige un grant `roomAdmin` con la sala nombrada, es decir, un segundo tipo de
  token de servidor con más privilegios que el que ya existe (`roomCreate`, que es el que exige
  `DeleteRoom`).
- `RemoveParticipant` devuelve error si el identity no está conectado (caso normal si el
  participante ya se había ido), lo que dispararía reintentos espurios del job.

Regla 4 de AGENTS.md: ante dos soluciones que cumplen el mismo requisito, la más simple.

## Riesgos aceptados

- **Ventana de exposición:** entre el disparo y el primer cierre exitoso, la sala sigue abierta
  (hasta ~1h20m en el peor caso con los tres reintentos). La desconexión local del frontend la
  mitiga, pero no es garantía: un cliente modificado la ignora.
- **Reintentos agotados:** tras el 3° reintento fallido solo queda un log `ERROR`. No hay canal de
  alerta al Admin hasta que exista infraestructura de notificaciones (AUD-014, FASE 2). Para ese
  momento el token del participante (TTL 1h) ya venció, así que nadie puede volver a entrar.
- **Reconexión con un token ya emitido:** un token vigente sigue siendo aceptado por LiveKit hasta
  su TTL. Si el servidor LiveKit tiene `auto_create` de salas habilitado, un cliente podría volver a
  unirse y recrear la sala. El token de participante ya no tiene `roomCreate` (AUD-002), pero **hay
  que verificar en la configuración del servidor LiveKit de cada entorno que `auto_create` esté
  deshabilitado**. Esto no se puede asegurar desde el código de Tinku.

## Alternativas consideradas

- **Fail-closed (abortar el corte si LiveKit falla):** descartada, ver arriba. Invierte la
  protección.
- **Reintento en memoria (`@Async`, `Thread.sleep`, `@Scheduled`):** prohibido por la regla 3 de
  AGENTS.md, porque un reinicio del proceso perdería el reintento de algo que involucra la
  seguridad de un menor.
- **Revocar tokens:** LiveKit no tiene revocación de tokens. El control equivalente es cerrar la
  sala y no emitir tokens nuevos, que es lo que se implementa.
