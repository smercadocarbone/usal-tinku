# ADR-M5-01 — Modo Bypass de la pasarela de pagos (flag global persistido)

## Estado
Aceptado (PR #19, pestaña "Salud de Infraestructura" de M8, 2026-09-12).
Formalizado **retroactivamente** (AGENTS §7): la decisión ya estaba tomada en
código para soportar el panel de infra y fue validada con la persona que
decide, que eligió el alcance completo — el toggle desactiva **de verdad** el
procesamiento de cobros reales de M5.

## Contexto
El panel M8 necesita poder **deshabilitar el cobro real por MercadoPago** sin
tocar código ni redeploy: para desarrollo con datos de seed, demos y
diagnóstico de Soporte Financiero, la plataforma debe seguir operando el flujo
completo (Reserva → confirmación → Sesión → escrow → liberación/reembolso) sin
mover dinero real.

El Spec_M5 no contempla este flag. Con el alcance completo decidido, el bypass
tiene que respetar tres invariantes:

1. **Nunca llamar al proveedor sin un id real:** una transacción nacida en
   bypass no tiene `mp_payment_id` de MercadoPago — llamarlo con un id falso
   sería inventar una operación con dinero que nunca existió.
2. **Mismo camino funcional:** confirmar, crear escrow y liberar o reembolsar
   debe seguir pasando por el flujo existente (eventos de dominio, Quartz,
   auditoría), sólo que sin efecto externo.
3. **Efecto inmediato y consistente:** el toggle de M8 debe impactar la
   siguiente operación de pago; no puede haber cached negativos ni ventanas
   donde una parte del flujo crea que está en bypass y otra no.

## Decisión
**Flag global persistido en base:** fila única `pagos.pasarela_estado` con
`habilitada` (default `true` = cobro real). Cada punto de M5 que toca al
proveedor **lee el flag de la base en el momento** — nunca hay caché en
memoria, lo que garantiza el efecto inmediato e uniforme del toggle.

- **Fail-closed hacia cobro real:** si la fila o la columna no existen
  (migración a medias) `estaHabilitada()` devuelve `true`. Un flag ausente
  puede dejar un cobro real sin proceso de bypass, pero nunca un cobro real
  tratado como simulado.
- Una preferencia generada en bypass devuelve `bypass=true` e `initPoint=null`
  (el frontend muestra "pago simulado"); la `Transaccion` se persiste marcada
  `en_bypass=true` con `mp_payment_id="bypass-<reservaId>"`.
- En el ciclo del escrow, `en_bypass` hace que liberación y reembolso sean
  sólo un cambio de estado local. Un reembolso **parcial** manual (M8) contra
  una transacción simulada responde **422** (no hay dinero real que
  parcializar).
- M8 expone `GET/PATCH /api/admin/financiero/pasarela`: lectura y toggle, sólo
  rol Soporte Financiero, acción auditada; la fila guarda `updated_by`.
- Regla de precedencia para M5-B/M5-D: un webhook real de MercadoPago que llega
  para una Reserva en bypass sigue siendo un pago real válido → se maneja con
  el flujo normal existente (`reembolsarPagoTardio` si llega tarde). El bypass
  desactiva la generación de cobros nuevos, no el procesamiento de uno que ya
  ocurrió.

## Alternativas descartadas / consideradas
- **Flag por variable de entorno / credencial vacía:** no permite alternar sin
  redeploy y ata una decisión de operación a config de arranque. Descartado:
  el toggle es una acción de Soporte en runtime.
- **Mock del proveedor como bean de perfil:** sólo vive en tests/dev, no
  aplica a un entorno desplegado con usuarios de seed. Descartado.
- **Flag en memoria con invalidation o TTL:** viola el efecto inmediato y
  agrega estado no persistido donde no hace falta. Descartado.
- **Bypass per-tenant / por Reserva:** innecesario para el contexto actual (1
  deploy piloto); el flag global es lo más simple que cumple el requisito.
  Si algún día se necesita granularidad, se revisa con otro ADR.

## Implementación
- **Migración** `V22__m5_pasarela_estado.sql`: tabla `pagos.pasarela_estado`
  (fila única `id=1 CHECK`, `habilitada NOT NULL`, `updated_at`,
  `updated_by → identidad.usuarios`) con seed `(1, TRUE, now(), NULL)` y
  columna `pagos.transacciones.en_bypass NOT NULL DEFAULT FALSE`.
- `EstadoPasarela` / `PasarelaEstadoRepository` /
  `PasarelaService.estaHabilitada()`/`establecerHabilitada(boolean, UUID)`.
- Guards en `PagoService.generarPreferencia` (ahora `@Transactional`),
  `EscrowService.reembolsarSiRetenida`, `LiberacionEscrowService.ejecutarLiberacion`
  y el reembolso parcial de `ColasFinancieroController`.
- **Tests:** `PasarelaBypassIntegracionTest` (E2E con Testcontainers): el
  proveedor queda `verifyNoInteractions` en toda la cadena bypass.

## Consecuencias
- M5 jamás contacta al proveedor con un `mp_payment_id` que no provino de un
  pago real — la invariante queda garantizada por el estado `en_bypass`, no por
  disciplina de los llamadores.
- El toggle es global (no por tenant): suficiente para el piloto; el ADR se
  revisa antes de escalar a multi-tenant.
- El flag vive en M5 (módulo dueño del dinero), no en M8: M8 sólo lo lee/escribe
  vía el servicio, conservando la regla de que la transacción de pagos es
  responsabilidad de `pagos`.

## Actualización 2026-09-24 — ámbito acotado (FASE2-07, AUD-018, P2 opción a)

El Modo Bypass **solo se puede activar fuera del perfil `prod`**. En producción
`PasarelaService.establecerHabilitada(false, …)` lanza `BypassNoPermitidoException` → **409**;
reactivar la pasarela se permite siempre. `GET /api/admin/financiero/pasarela` suma
`bypassPermitido` para que el panel deshabilite el control con una explicación.

Controles compensatorios fuera de `prod`: log `WARN` con el id del admin cada vez que se apaga la
pasarela (además de la auditoría del interceptor de M8) y un **banner persistente** en todo `/admin`
mientras esté apagada. Se descartaron el TTL con Quartz (opción b: un plazo más en la Tabla de
Tiempos y un job, para un riesgo que la opción a ya elimina en producción) y la doble confirmación
(opción c: no evita un toggle olvidado).

## Registro de Decisiones Técnicas (Constitución)
No aplica: no existe fila del Registro que cubra el flag de pasarela. La
decisión queda documentada en este ADR (decisión de producto/técnica del
incremento, no una elección de proveedor del Registro).