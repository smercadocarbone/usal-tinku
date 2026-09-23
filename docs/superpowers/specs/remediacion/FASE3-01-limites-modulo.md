# FASE3-01 — Tres movimientos acotados de límites de módulo (AUD-019, AUD-022)

**Branch:** `aud/fase3-p2-calidad` · **Riesgo:** medio (refactor que toca muchos imports) ·
**Findings:** AUD-019 (parcial), AUD-022 · **Precondición:** FASE 2 mergeada en `main`.

> **Regla de esta tarea:** son **tres commits separados**, en este orden, con la **suite completa
> verde entre cada uno**. Un refactor de imports que rompe cientos de tests a la vez es irrevisable.
> **Solo** estos tres movimientos: los otros accesos cruzados a repositorios son deuda aceptada en
> `docs/adr/ADR-000-03.md`. No "arregles de paso" nada más (A10).

Estos cambios no alteran comportamiento: **no** requieren test RED. El criterio es que la suite
completa quede igual, sin bajas.

## Movimiento 1 — Romper el ciclo `shared ↔ admin`

**Hoy:** `shared/AdminModeracionGate.java` importa `admin.model.Admin`, `admin.model.RolAdmin` y
`admin.repository.AdminRepository`, mientras `admin` depende de `shared`. Es un ciclo.

**Qué hacer:** mover `AdminModeracionGate` al paquete `com.tinku.admin` (por ejemplo
`admin/AdminModeracionGate.java`). Es la opción que no mueve entidades ni tablas. Actualizá los
imports de todos sus usuarios (`rg -ln "AdminModeracionGate" backend/src`).

**Verificá después** que `shared` no importe nada de módulos de dominio:
`rg -n "^import com.tinku\.(admin|aula|identidad|matching|pagos|reservas|resumen|reputacion|seguridad)" backend/src/main/java/com/tinku/shared`
tiene que dar **cero** resultados. Si queda alguno, reportalo; no lo arregles si no está en esta spec.

Commit: `refactor(admin): mover AdminModeracionGate a admin y romper el ciclo con shared (AUD-019)`.

## Movimiento 2 — `AlertaSeguridad` a `seguridad`

**Hoy:** la entidad central del track de Alertas de M9 vive en `aula/model/AlertaSeguridad.java`,
con `@Table(name = "alertas_seguridad", schema = "aula")`.

**Qué hacer:** mover la clase (y `aula/repository/AlertaSeguridadRepository.java`) a
`seguridad/model` y `seguridad/repository`. **La tabla NO se mueve de schema:** `@Table` queda
igual (`schema = "aula"`), así que **no hay migración**. Es solo el paquete Java. Actualizá imports.

Commit: `refactor(seguridad): mover AlertaSeguridad al modulo que la gobierna (AUD-019)`.

## Movimiento 3 — Cada evento en el módulo que lo publica (AUD-022)

**Hoy:** los eventos viven en el módulo **consumidor**, no en el emisor. Por ejemplo, los
`Sesion*Event` (que publica M3) están en `pagos/evento`, y `DenunciaResueltaEvent` (que publica M9)
está en `reservas/evento`.

**Qué hacer:** mover cada clase al paquete `evento` del módulo que la **publica**:

| Clase | Publica | Destino |
|---|---|---|
| `SesionEvento` y los `Sesion*Event` | `aula` | `aula/evento` |
| `DenunciaRegistradaEvent`, `DenunciaResueltaEvent` | `seguridad` | `seguridad/evento` |
| `AlertaResueltaEvent` | `seguridad` | `seguridad/evento` |
| `Reserva*Event`, `ReservaEvento` | `reservas` | quedan en `reservas/evento` |
| `SancionAplicadaEvent` | `seguridad` | queda donde está |

Verificá quién publica cada uno con `rg -n "publishEvent(new <Clase>"` antes de moverlo; si el
emisor real no coincide con la tabla, gana el código: reportalo.

**Los nombres de los eventos NO cambian** (el string de `getNombre()`, A2). Solo el paquete.

Commit: `refactor: mover cada evento de dominio al modulo que lo publica (AUD-022)`.

## Criterios de aceptación

- Tres commits, suite completa verde y **con el mismo conteo** en cada uno.
- `REGISTRO_FINDINGS.md`: AUD-022 → `CERRADO`; AUD-019 anota los dos movimientos y sigue en
  `ACEPTADO` por el resto, con referencia a ADR-000-03 (si hoy dice `ABIERTO`, pasalo a `ACEPTADO`
  citando el ADR).
- `docs/adr/ADR-000-03.md`: subsección "Actualización" con lo que se movió y lo que sigue aceptado.
