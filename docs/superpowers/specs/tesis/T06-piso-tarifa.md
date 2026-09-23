# T06 — Piso de tarifa de USD 4 por hora (DT2)

**Branch:** `tesis/piso-tarifa` · **Riesgo:** medio (dinero) ·
**Bloqueada por:** `FASE2-01-disponibilidad-bloques-30.md` **mergeada** (introduce `precioHora`,
D6). PT3 y PT4 ya resueltas.

## 1. Problema (verificado al 2026-09-23)

`pagos.tarifas_tutor` (V17) guarda un `precio_sesion` con solo `CHECK (precio_sesion >= 0)`.
FASE2-01 lo reemplaza por un precio **por hora** (D6: `precio = precioHora × unidades / 2`). La
tesis (Cap. 5) fija un **piso de USD 4 por hora**: por debajo, la comisión no cubre los costos de
operación, y el precio de equilibrio del escenario base (USD 3,78/h) queda por debajo del piso,
que funciona como resguardo del modelo.

## 2. Decisiones resueltas (PT3, PT4 — 2026-09-23: se aplican las recomendaciones)

- PT3: valor en ARS y forma de actualizarlo. Recomendación: property
  `tinku.tarifa.piso-hora-ars: 6140`, revisada mensualmente por un Admin (redeploy o variable de
  entorno de Coolify). **Sin** consumir una API de tipo de cambio.
- PT4: tarifas existentes por debajo del piso. Recomendación: no retroactivo; se exige al editar.

## 3. Implementación (con las recomendaciones)

1. Bean `PisoTarifa` en `pagos` que expone el valor (un único lugar).
2. Validación en el servicio que actualiza la tarifa (`PagoController` →
   `ActualizarTarifaTutorRequest`, o donde FASE2-01 deje la edición del `precioHora`):
   `precioHora < piso` → `TarifaBajoPisoException` → **422** con el piso en el cuerpo.
3. `GET` de la tarifa del Tutor (`TarifaTutorResponse`) suma `pisoHora` para que el frontend
   valide antes de enviar.
4. Frontend (panel del Tutor, edición de tarifa): mínimo en el input + mensaje; aviso visible si
   su tarifa vigente está por debajo del piso (PT4).
5. `docs/specs/Spec_M5_Motor_Pagos.md`: regla nueva `BR-PAG-XX` (piso), con referencia a la tesis.

## 4. Tests (RED primero)

1. `actualizarTarifa_bajoPiso_422`.
2. `actualizarTarifa_igualAlPiso_ok` (borde).
3. `tarifaExistenteBajoPiso_noSeModificaAlDesplegar` (PT4).
4. `precioReserva_seCalculaConPrecioHoraVigente` (regresión de D6, no duplicar el test de FASE2-01).

## 5. Criterios de aceptación

- Suite verde. Ninguna Reserva ya creada cambia de precio (precio congelado).
- T-TES-06 tildada.

## 6. NO tocar

- `pagos.precios_referencia_regional` (FR-ADM-007): es una **sugerencia** por provincia, no un
  piso. No mezclar las dos reglas.
