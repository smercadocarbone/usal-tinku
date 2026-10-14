# T16 — Comisión de plataforma otra vez al 27 % (DT1, revisión de la tesis del 2026-10-13)

**Branch:** `tesis/comision-27-v2` · **Riesgo:** medio (dinero) · **Bloqueada por:** —
**Supera a:** T-TES-15 / ADR-M5-04 (21 %). **Decisión del dueño (2026-10-14):** la comisión es 27 %.

## 1. Qué cambió en la tesis

El Cap. 5 vigente (`usal-tinku-docs`, `capitulos/05-justificacion-economica.tex`, macro
`\comision{}` = 27 %) vuelve al **27 %** y lo defiende con un modelo recalculado:

| Dato | Tesis vigente | Lo que dice el repo hoy |
|------|---------------|-------------------------|
| Comisión | **27 %** | 21 % (`application.yml`, ADR-M5-04) |
| Ticket promedio | **USD 7,42** (414 avisos de Superprof × 0,85 ÷ $1.535) | USD 7 |
| Margen neto por sesión | **USD 1,5552** = 2,0034 − 0,4482 (pasarela 6,04 %) | — |
| VAN base / TIR base | **USD 3.573 / 20,47 %** | "se anula recién con 12,4 %" (ADR-M5-04) |
| Punto de equilibrio de la comisión | **≈ 25,3 %** (a 21 %: VAN −9.228; a 23 %: −4.961) | 12,4 % |

Consecuencia: **la premisa de ADR-M5-04 ("la tesis ya está recalculada con 21 %, el VAN se anula
con 12,4 %") dejó de ser verdad.** Con la tesis vigente, 21 % da VAN negativo. Por eso la
comisión vuelve a 27 %, y ADR-M5-04 queda superado.

## 2. Decisión que hay que dejar escrita (ADR obligatorio, AGENTS §2)

Crear `docs/adr/ADR-M5-05.md` — "Comisión de plataforma al 27 % (supera ADR-M5-04)":

1. **BR-PAG-01 = 27 %** del monto bruto de la sesión, a cargo del Tutor. Sigue siendo
   configuración (`tinku.mercadopago.marketplace-fee-percent`).
2. **Modelo de cobro A (ADR-M5-02) no cambia.** MercadoPago descuenta su comisión (6,04 % con
   IVA) de los fondos del Tutor *antes* del `marketplace_fee`. Consecuencia declarada: el Tutor
   recibe **1 − 0,27 − 0,0604 = 66,96 %** del precio (con 21 % recibía 72,96 %).
3. **Discrepancia con la tesis, a documentar y no a "arreglar" en silencio:** el margen de la tesis
   (USD 1,5552) descuenta la pasarela **a cargo de Tinku**. Con el modelo A, la pasarela la paga el
   Tutor, así que el margen real de Tinku por sesión es el bruto (USD 2,0034 a ticket 7,42): el
   modelo de la tesis es una **cota inferior conservadora**. Es correcto para defender el
   proyecto, pero el Cap. 5 dice "costo de pasarela" como costo de Tinku y el Cap. 4 deja "abierto"
   el OAuth por tutor que el código ya implementa. **Pendiente de la tesis, no del código**
   (ver §6).
4. **Qué se pierde:** el argumento de ADR-M5-04 sobre R-02 (desintermediación) y R-07 (cuesta más
   sumar Tutores) vuelve a valer, con 6 puntos menos de neto para el Tutor. El dueño lo acepta
   (DT1). La métrica para revisar la comisión tras el piloto sigue diferida (BR-PAG-03).
5. **Alternativa descartada:** que Tinku absorba la comisión de MercadoPago para igualar el modelo
   de la tesis. Con modelo A el `marketplace_fee` es un porcentaje fijo y la comisión de MP es
   variable según el plazo de acreditación de cada cuenta: Tinku tendría que conocer la comisión
   real de cada pago para compensarla. Es una dependencia nueva, no una configuración.
6. **Sin backfill.** Las transacciones existentes conservan la comisión con la que se crearon (21 %
   las del 28/09 en adelante, 27 % las anteriores). Ninguna migración aplicada se edita.

Marcar `ADR-M5-04.md` como **"Superado por ADR-M5-05"** (cambiar solo la línea `Estado`; el resto
queda como historial).

## 3. Implementación

1. `backend/src/main/resources/application.yml`: `marketplace-fee-percent: 27`.
2. `ComisionPlataforma`: default del `@Value` a `27` (siguen siendo la única fuente: la preferencia
   y `Transaccion.comisionPlataforma` dan siempre el mismo número).
3. `deploy/docker-compose.coolify.yml`, `docs/operacion/RUNBOOK_produccion.md`,
   `deploy/demo/cargar-demo.sql`, `README.md`: `rg -n "21 ?%|marketplace-fee|MARKETPLACE_FEE"` y
   alinear cada valor o texto que cite el 21 %.
4. Frontend, pestaña Precio (FR-PAG-019, `TabPrecio.tsx`): el texto sale del backend
   (`comisionPorcentaje`), así que **no hay número que cambiar**; verificar que el desglose
   "Tinku 27 % + MercadoPago ~6,04 %" se vea bien y que el neto por hora sea el 66,96 %.
5. `docs/specs/Spec_M5_Motor_Pagos.md`:
   - FR-PAG-003 y el historial de BR-PAG-01 → 27 %, agregando una fila al historial (15 → 27 →
     21 → 27) con el motivo de la tesis vigente. **No borrar** el texto anterior.
   - Corregir el criterio de aceptación de la liberación ("descontando la comisión del 27%" ya
     estaba bien; el resto del archivo no debe contradecirlo).
6. `docs/superpowers/specs/tesis/T05-comision-27.md`: reemplazar el cartel "Superado por
   ADR-M5-04" por "Superado por ADR-M5-04, que a su vez fue superado por T16/ADR-M5-05".
7. `00-LEEME-tesis.md`: fila DT1 y fila "Comisión" de la tabla de §1 → 27 % (ver §5 de esta spec).

## 4. Tests (RED primero)

1. `ComisionPlataformaTest`: `calcular(15000.00) == 4050.00` (hoy 3150.00 → rojo) y
   `porcentaje() == 27`.
2. Tests que **afirman** la comisión calculada (los de 3150.00: `PagosFlujosIntegracionTest`,
   `PagosWebhookIntegracionTest`, `PasarelaBypassIntegracionTest`, `E2EFlujoFelizIntegracionTest`;
   y `PagosFlujosIntegracionTest` ~L677/L880 que esperan `21`): pasan a 4050.00 / 27. **A7:** estos
   tests codificaban la regla de negocio vieja, no un bug: decirlo en el commit.
3. `PaqueteIntegracionTest` (~L182, `9072.00`): recalcular a mano con 27 % clase por clase
   (ADR-M5-03) y justificarlo en el commit.
4. Los tests que solo **setean** `setComisionPlataforma(...)` como fixture no dependen del
   porcentaje: **no tocarlos**.
5. `frontend/tests/cuenta/precio.spec.ts` (~L113): el mock con `comisionPorcentaje: 21` pasa a 27
   y el neto esperado se recalcula.
6. Regresión: un reembolso total devuelve el bruto completo y no depende del porcentaje.

## 5. Criterios de aceptación

- Suite verde, con la línea `Tests run:` leída (AGENTS §5); N no baja respecto del baseline.
- `rg -n "21 ?%" docs backend/src/main frontend/src deploy README.md` no deja ningún 21 % vivo
  fuera de los historiales (ADR-M5-04, historial de BR-PAG-01, Tasks).
- T-TES-16 tildada en `Tasks_Tinku_Implementacion.md` **y** `Tasks_Tinku_Chunks.md`.

## 6. Para la tesis (no para este repo)

Se lo señalo al autor para que decida; no lo arregla ninguna spec de código:

- Cap. 5, tabla de economía unitaria: la pasarela (6,04 %) figura como costo de Tinku, pero con el
  modelo A la paga el Tutor.
- Cap. 4: "Queda abierta una decisión… OAuth por tutor" ya está implementada (ADR-M5-02).
- Cap. 5, reembolsos: "la pasarela reintegra su propia comisión" depende de que el reembolso se
  haga con el token del Tutor (ya es así en M5).

## 7. NO tocar

- Las transacciones ya creadas: conservan su comisión congelada.
- El adicional de resumen: sigue entrando entero al `marketplace_fee` y la comisión no se calcula
  sobre él (BR-PAG-11; ver T17 para su nuevo precio).
- `ComisionPlataforma` sigue siendo el único cálculo.
