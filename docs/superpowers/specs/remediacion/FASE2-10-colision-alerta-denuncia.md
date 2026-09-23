# FASE2-10 — Separar la pausa del kill-switch de la pausa por Denuncia (AUD-005, riesgo abierto de ADR-M3-02)

**Branch:** `aud/fase2-p1-integridad` · **Riesgo:** medio (mueve dinero) · **Findings:** cierra el riesgo
aceptado "Colisión con una Denuncia sobre la misma sesión" de `docs/adr/ADR-M3-02.md` y la parte
pendiente de T-AUD-024.

## 1. Problema (verificado en el código al 2026-09-22)

Desde FASE 1, el kill-switch (`EscrowService.onSesionKillswitchMenor/Adultos`) y la Denuncia
(`EscrowService.onDenunciaRegistrada`) dejan la transacción en el **mismo** estado,
`EstadoTransaccion.PAUSADO_DENUNCIA`. Los dos cierres también leen ese mismo estado:

- `EscrowService.onAlertaResuelta` → si está `PAUSADO_DENUNCIA`, reembolsa al Estudiante.
- `EscrowService.onDenunciaResuelta` → si está `PAUSADO_DENUNCIA`: `INFUNDADA` reprograma la
  liberación al Tutor, `FUNDADA` libera al Tutor, `ESCALADA` reembolsa.

**Escenario que rompe:** kill-switch con menor en la sesión S → escrow en pausa → el Estudiante
además presenta una Denuncia con `sesionId = S` → un Admin resuelve primero la Denuncia como
`INFUNDADA` → **M5 le reprograma la plata al Tutor mientras la Alerta de Seguridad sigue sin
revisar**. Cuando después se resuelve la Alerta, `onAlertaResuelta` es no-op porque la
transacción ya no está en pausa. Un Tutor bajo investigación por contenido ilegal frente a un
menor cobra antes de que se revise la Alerta.

## 2. Decisión de diseño (del arquitecto; no requiere preguntar)

Estado nuevo **`pausado_alerta`**, con una regla de precedencia explícita: **la Alerta manda
sobre la Denuncia**, porque es el track de seguridad del menor (Artículo II).

| Estado actual | Evento | Estado nuevo | Dinero |
|---|---|---|---|
| `retenido_escrow` | `sesion.killswitch_*` | `pausado_alerta` | se cancela la liberación agendada |
| `pausado_denuncia` | `sesion.killswitch_*` | `pausado_alerta` | ídem (la Alerta pasa a mandar) |
| `retenido_escrow` | `denuncia.registrada` | `pausado_denuncia` | (sin cambio respecto de hoy) |
| `pausado_alerta` | `denuncia.registrada` | `pausado_alerta` | **no cambia**: ya está pausado por algo más grave |
| `pausado_alerta` | `denuncia.resuelta` (cualquier resolución) | `pausado_alerta` | **no se mueve plata**; log `INFO` con reservaId |
| `pausado_alerta` | `alerta.resuelta` | `reembolsado` | reembolso total (D3, sin cambio) |
| `pausado_denuncia` | `alerta.resuelta` | sin cambio | no-op (la pausa no era de una Alerta) |

Se descarta que M5 consulte si hay Alertas pendientes: acoplaría M5 al modelo de M9, y un estado
explícito es más simple de leer, de testear y de mostrar en el panel.

## 3. Archivos

- **Nuevo:** `backend/src/main/resources/db/migration/V<siguiente>__m5_estado_pausado_alerta.sql`
- `backend/src/main/java/com/tinku/pagos/model/EstadoTransaccion.java`
- `backend/src/main/java/com/tinku/pagos/service/EscrowService.java`
- `backend/src/main/java/com/tinku/admin/web/ColasFinancieroController.java` (el reembolso
  parcial acepta `PAUSADO_DENUNCIA`: decidir si también `PAUSADO_ALERTA` → **no**, ver §5)
- Tests: `backend/src/test/java/com/tinku/pagos/service/EscrowListenersIntegracionTest.java`
- Frontend: `rg -n "pausado" frontend/src` y agregar la etiqueta del estado nuevo donde se
  muestren estados de transacción (hoy no hay ninguna ocurrencia: verificarlo).

## 4. Pasos

1. **Migración.** Primero averiguá el nombre real del CHECK anónimo de V11:
   ```sql
   SELECT conname FROM pg_constraint
   WHERE conrelid = 'pagos.transacciones'::regclass AND contype = 'c'
     AND pg_get_constraintdef(oid) LIKE '%estado%';
   ```
   (esperado: `transacciones_estado_check`). La migración nueva hace `DROP CONSTRAINT <ese>` y
   `ADD CONSTRAINT chk_transacciones_estado CHECK (estado IN ('retenido_escrow', 'liberado',
   'reembolsado', 'pausado_denuncia', 'pausado_alerta'))`. **No editar V11** (A1).
2. `EstadoTransaccion`: agregar `PAUSADO_ALERTA("pausado_alerta")`.
3. `EscrowService`:
   - Los dos listeners de kill-switch pasan de `pausarSiRetenida` a un método nuevo
     `pausarPorAlerta(reservaId)` que aplica las dos primeras filas de la tabla.
   - `onDenunciaRegistrada` sigue con `pausarSiRetenida` (que solo actúa sobre `RETENIDO_ESCROW`,
     así que ya respeta la fila de `pausado_alerta`). Verificalo con el test.
   - `onDenunciaResuelta`: si la transacción está `PAUSADO_ALERTA`, log y `return` antes del switch.
   - `onAlertaResuelta`: pasa a mirar `PAUSADO_ALERTA` en vez de `PAUSADO_DENUNCIA`.
4. Actualizar el javadoc de los métodos tocados y de `AlertaResueltaEvent`.

## 5. Tests obligatorios (RED primero, en `EscrowListenersIntegracionTest`)

1. `denunciaResueltaInfundada_conAlertaPendiente_noLiberaAlTutor` — kill-switch → Denuncia
   registrada → Denuncia resuelta `INFUNDADA` → la transacción sigue en pausa, sin liberación
   agendada (`LiberacionEscrowService.triggerLiberacion(id)` no existe) y sin llamadas a
   `liberacion`. **Este es el test que tiene que fallar antes del fix.**
2. `alertaResuelta_trasDenunciaResueltaAntes_igualReembolsa` — misma secuencia + `alerta.resuelta`
   → `REEMBOLSADO`, `reembolsarTotal` invocado una vez.
3. `killswitchSobreEscrowYaPausadoPorDenuncia_pasaAPausadoAlerta`.
4. `denunciaRegistradaSobrePausadoAlerta_noCambiaElEstado`.
5. Regresión: los tests existentes `sesionKillswitchMenor_pausaElEscrowSinReembolsar` y
   `sesionKillswitchAdultos_pausaElEscrowSinReembolsar` pasan a esperar `PAUSADO_ALERTA`.
   Cambiarlos está justificado (el estado esperado cambió por diseño): decilo en el commit.

**Reembolso parcial en `ColasFinancieroController`:** no aceptar `PAUSADO_ALERTA`. Un reembolso
parcial manual sobre dinero congelado por una Alerta de seguridad adelantaría la decisión de M9.
Test: `reembolsoParcial_sobrePausadoAlerta_422`.

## 6. Criterios de aceptación

- Los 4 tests nuevos y el de reembolso parcial en verde; la suite completa sin bajas.
- `REGISTRO_FINDINGS.md`: la fila AUD-005 suma el commit y los tests.
- `docs/adr/ADR-M3-02.md`: la sección "Colisión con una Denuncia…" pasa de riesgo aceptado a
  **resuelto**, con el hash. **No borres el texto original** (Artículo XII): agregá una
  subsección "Actualización".
- `docs/specs/Spec_M5_Motor_Pagos.md` §2: documentar `pausado_alerta` y la precedencia.

## 7. NO tocar

- `SesionService` y el corte: el Artículo II queda intacto.
- Los nombres de los eventos (A2).
- `LiberacionEscrowService`: su guard ya ignora todo lo que no sea `RETENIDO_ESCROW`.
