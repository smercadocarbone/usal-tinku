# T17 — Precio del adicional de resumen según la duración de la clase (DT3, revisión de la tesis del 2026-10-13)

**Branch:** `tesis/resumen-precio-duracion` · **Riesgo:** medio (dinero) · **Bloqueada por:** —
(independiente de T16: el adicional va entero al `marketplace_fee`, no depende del porcentaje)

## 1. Qué cambió en la tesis

El Cap. 5 vigente (sección "Resumen automático como adicional opcional") ya **no** fija un monto
(USD 0,50 ≈ $770): lo calcula **a costo más 50 %**, y por eso **crece con la duración**:

```
P_resumen(d) = 1,50 × C_IA(d)
C_IA(d)      = c_t·d + c_e·τ·d + c_s·σ            (d = minutos de la clase)
```

| Parámetro | Valor | Origen en la tesis |
|-----------|-------|--------------------|
| `c_t` transcripción, USD/min | 0,0030 | `gpt-4o-mini-transcribe` |
| `c_e` entrada del LLM, USD/token | 2,5·10⁻⁶ | GPT-4o, USD 2,50 por millón |
| `τ` tokens de transcripción por minuto | 217 | ~13.000 por hora de clase |
| `c_s` salida del LLM, USD/token | 1·10⁻⁵ | GPT-4o, USD 10 por millón |
| `σ` tokens de salida + instrucciones | 840 | resumen de 5 puntos + instrucciones fijas |
| margen | 1,50 | costo + 50 % |
| tipo de cambio | $1.535/USD | el mismo del piso tarifario (BNA 09/09/2026) |

Resultado (USD → ARS, **redondeado hacia arriba a la decena de pesos**):

| Duración | Costo IA (USD) | Precio (USD) | **Precio (ARS)** |
|----------|---------------|--------------|------------------|
| 30 min | 0,1147 | 0,1720 | **270** |
| 51 min (promedio) | 0,1890 | 0,2835 | **440** |
| 60 min | 0,2209 | 0,3313 | **510** |
| 90 min | 0,3272 | 0,4907 | **760** |
| 120 min | 0,4334 | 0,6501 | **1.000** |
| 180 min | 0,6459 | 0,9688 | **1.490** |

(La columna ARS la verifiqué recalculando la fórmula con los parámetros; coincide con la tabla de
la tesis.)

Otros cambios del mismo capítulo que **no tocan código** pero conviene saber:

- El adicional **no entra al flujo de fondos** de la tesis (aporta ~USD 0,077 por resumen promedio
  tras la pasarela): su función es que el costo de IA no recaiga en las sesiones sin resumen.
  El supuesto S-04 ("lo contrata el 40 %") que citaba T09 ya no existe en la tesis.
- Si el resumen falla, se reembolsa el adicional **íntegro** (igual que BR-PAG-11) y el costo de
  transcripción ya incurrido no se recupera: se declara como restricción, sin cambio de código.

## 2. Hoy en el repo (verificado 2026-10-14)

- Precio **fijo $770**: `AdicionalResumen.precio()` (`tinku.resumen.adicional.precio-ars`, default
  770 también en `application.yml`, `deploy/docker-compose.coolify.yml` y el RUNBOOK).
- `ReservaService.crearReserva` congela `adicionalResumen.precio()` en
  `Reserva.precioAdicionalResumen` — sin mirar `duracionMinutos`.
- `GET /api/reservas/adicional-resumen?tutorId=` devuelve `{disponible, precio}` sin duración, y
  `reservar/page.tsx` muestra "(+$770)" fijo.
- Spec_M5 BR-PAG-11 y Spec_M4 (§ adicional) dicen "$770".

## 3. Decisiones que hay que dejar escritas (ADR obligatorio, AGENTS §2)

Crear `docs/adr/ADR-M5-06.md` — "Precio del adicional de resumen: costo + 50 % por duración
(modifica BR-PAG-11)":

1. **Fórmula y parámetros arriba**, como configuración (§4.1), no constantes en código.
2. **`d` = `Reserva.duracionMinutos`**, la duración **agendada** (franja de 30 a 180 min, múltiplos
   de 30). Es lo que se conoce al reservar, y el precio se congela en ese momento como el de la
   sesión. No se reprecia si la clase dura menos o más en los hechos.
3. **Redondeo:** hacia arriba a la decena de pesos, `BigDecimal` (nada de `double` en dinero).
4. **Paquete mensual:** sigue sin ofrecer el adicional (ADR-M5-03). Sin cambios.
5. **Menores:** el adicional sigue sin ofrecerse con un Menor (Art. II). Sin cambios.
6. **Tipo de cambio:** el parámetro nuevo `tinku.resumen.adicional.tipo-cambio-ars` (1535) es
   **separado** del piso tarifario (`tinku.tarifa.piso-hora-ars`, 6140, PT3). Ambos nacen del mismo
   $1.535, pero se mantiene el piso como valor explícito en ARS: unificar en un solo tipo de
   cambio es más prolijo pero cambia el contrato de T06, y AGENTS §1.4 pide la solución más simple.
   La revisión mensual de un Admin (PT3) revisa los dos juntos; dejarlo anotado en el RUNBOOK.
7. **Las tarifas de OpenAI son de lista y pueden cambiar** (la tesis lo dice): por eso son
   configuración y el RUNBOOK las lista. Verificarlas contra la página oficial antes de habilitar
   `tinku.resumen.adicional.habilitado=true` en producción.
8. **Reservas ya creadas** conservan los $770 congelados; sin backfill ni migración de datos. Los
   reembolsos del adicional usan el monto congelado de la Reserva, así que no cambian.
9. **Constitución / Tabla de Tiempos:** no hay plazo nuevo. Si la Constitución cita "$770" en algún
   lugar (rg), actualizar en el mismo commit (AGENTS §8).

## 4. Implementación

1. **Config** (`application.yml`, mismo patrón `${ENV:default}` del bloque
   `tinku.resumen.adicional`):
   ```yaml
   tinku.resumen.adicional:
     costo-transcripcion-usd-min: ${TINKU_RESUMEN_COSTO_TRANSCRIPCION_USD_MIN:0.0030}
     costo-entrada-usd-token:     ${TINKU_RESUMEN_COSTO_ENTRADA_USD_TOKEN:0.0000025}
     tokens-por-minuto:           ${TINKU_RESUMEN_TOKENS_POR_MINUTO:217}
     costo-salida-usd-token:      ${TINKU_RESUMEN_COSTO_SALIDA_USD_TOKEN:0.00001}
     tokens-salida:               ${TINKU_RESUMEN_TOKENS_SALIDA:840}
     margen:                      ${TINKU_RESUMEN_MARGEN:1.50}
     tipo-cambio-ars:             ${TINKU_RESUMEN_TIPO_CAMBIO_ARS:1535}
   ```
   Retirar `precio-ars`. Agregar las variables al `deploy/docker-compose.coolify.yml` y al
   RUNBOOK (donde hoy está `TINKU_RESUMEN_ADICIONAL_PRECIO_ARS`). Si el default del `@Value` y el
   del yml difieren, gana el yml: dejar el mismo en ambos.
2. **`AdicionalResumen`:** reemplazar `precio()` por `precio(int duracionMinutos)` con la fórmula
   de §1. `validarContratacion`/`permiteGrabar` no cambian. Validar `duracionMinutos > 0`.
3. **`ReservaService.crearReserva`:** `reserva.setPrecioAdicionalResumen(adicionalResumen.precio(duracionMinutos))`.
4. **`GET /api/reservas/adicional-resumen`:** agregar `duracionMinutos` (parámetro). Respuesta:
   `{disponible, precio}` con el precio para esa duración. Para no romper clientes, sin parámetro
   usa 60 min. Es un contrato público: dejar el cambio anotado en el Spec de M4.
5. **Frontend (`reservar/page.tsx`, `lib/api.ts`):** el precio del checkbox y el total
   (`precioElegido + adicional.precio`, L466) deben **recalcularse al cambiar la duración
   elegida** (hoy se pide una sola vez por Tutor). Mostrar "+$510" para 60 min, etc.
6. **Specs:** `Spec_M5` BR-PAG-11 ("$770" → "costo + 50 % según la duración, ver ADR-M5-06"),
   `Spec_M4` (párrafo del adicional y el GET), `Spec_M6` si cita el monto.
7. **Docs que citan $770:** `ADR-M3-04.md` (L35, "USD 0,18 por hora… dentro del precio del
   adicional ($770 ≈ USD 0,50)") — **no editar el ADR**: agregar una línea "ver ADR-M5-06" en el
   nuevo ADR; `T09-adicional-resumen.md` y `00-LEEME-tesis.md` (DT3, PT5) con nota "superado por
   T17".

## 5. Tests (RED primero)

1. `AdicionalResumenTest`: `precio(30)=270`, `precio(60)=510`, `precio(90)=760`, `precio(120)=1000`, `precio(180)=1490`; `precio(0)` y
   negativos lanzan `IllegalArgumentException`. **Cuidado con el redondeo:** probar también un
   valor justo en la decena (que no suba de más) con parámetros inyectados.
2. `PagosFlujosIntegracionTest.reservaConAdicional_adulto…`: hoy espera `770` y `15770`; pasa a
   depender de la duración de la reserva del test (calcular con la misma fórmula y comentar el
   porqué). La comisión sigue siendo **solo sobre la sesión** y el `marketplace_fee` = comisión +
   adicional entero (con el % que corresponda tras T16, derivado del `@Value`, no hardcodeado).
3. Nuevo: reservar con el adicional a 30 y a 120 min da dos `precioAdicionalResumen` distintos y
   se congelan (cambiar la config después no altera la Reserva).
4. Nuevo: `GET /adicional-resumen?tutorId=…&duracionMinutos=90` → 760.
5. Frontend (Playwright, `tests/reservar/*`): al pasar de 60 a 120 min el "+$510" pasa a "+$1.000"
   y el total se actualiza.
6. Regresión: reserva **sin** adicional → mismo monto y comisión; reserva de Menor con adicional →
   **422** (sin cambios); fallo del resumen → reembolso por el monto congelado exacto.
7. Los tests que fijan `770` como **fixture** (`AdminPanelIntegracionTest`, `ResumenIntegracionTest`,
   `BajaMenorAnonimizacion…`) no dependen de la fórmula: **no tocarlos**.

## 6. Criterios de aceptación

- Suite verde con `Tests run:` leída; N no baja.
- `rg -n "770" backend/src/main frontend/src deploy docs/specs docs/operacion README.md` sin
  ningún 770 vivo (solo historiales y fixtures de test).
- Spec M4, M5 y M6 actualizados; ADR-M5-06 escrito; T-TES-17 tildada en los dos archivos de tareas.

## 7. NO tocar

- Las 4 condiciones de grabación del Art. V (`validarContratacion`, `permiteGrabar`): el precio no
  cambia quién puede grabar.
- El mecanismo de reembolso del adicional (outbox, backoff 5/15/60 min, cola de Soporte
  Financiero, R4): solo cambia de dónde sale el monto congelado.
- `ADR-M6-03` (GPT-4o): la tesis vigente coincide con él (GPT-4o resumen + `gpt-4o-mini-transcribe`
  transcripción). **Ojo:** `00-LEEME-tesis.md` DT4 todavía dice "Gemini 3.5 Flash-Lite"; está
  superado por ADR-M6-03 y conviene anotarlo ahí, sin tocar código.
