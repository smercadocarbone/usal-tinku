# FASE2-07 — Acotar el Modo Bypass de la pasarela (AUD-018)

**Branch:** `aud/fase2-p1-integridad` · **Riesgo:** bajo · **Finding:** AUD-018 ·
**Bloqueada por:** **P2**.

## 1. Problema (verificado al 2026-09-22)

`pagos/service/PasarelaService.establecerHabilitada(boolean, UUID)` apaga la pasarela real
(V22, `pagos.pasarela_estado`). Mientras está apagada, cada Reserva se confirma **sin cobro**
(`Transaccion.enBypass = true`). Lo expone `admin/web/ColasFinancieroController` (`/pasarela`) a
un Admin de Soporte Financiero. **No tiene límite de tiempo, ni restricción de entorno, ni
aviso**: un toggle olvidado, o un admin comprometido, deja **el marketplace gratis en
producción** hasta que alguien lo note. `docs/adr/ADR-M5-01.md` documenta la decisión, pero no
acota su alcance.

## 2. PARAR (P2)

Opciones: (a) solo habilitable fuera de `prod`; (b) TTL con un job de Quartz que reactiva la
pasarela; (c) doble confirmación. **Recomendación: (a)** + banner persistente + log. Es la más
simple y cierra el riesgo real. Si el usuario elige (b), el TTL es un número de tiempo: tiene que
entrar a la Tabla de Tiempos (A3) y el job va por Quartz persistido (A4).

## 3. Implementación de la opción recomendada (a)

**Archivos:** `PasarelaService.java`, `ColasFinancieroController.java`, `ADR-M5-01.md`,
frontend: `components/admin/AdminInfrastructurePanel.tsx`, `app/admin/salud/page.tsx` y el layout
de `/admin` (para el banner).

1. `PasarelaService.establecerHabilitada(false, …)` con el perfil `prod` activo
   (`environment.acceptsProfiles(Profiles.of("prod"))`) → excepción nueva
   `BypassNoPermitidoException` → **409** en `ColasFinancieroController` (por su
   `AdminExceptionHandler`). Reactivar (`true`) se permite siempre.
2. Log `WARN` con el id del admin cada vez que se apaga la pasarela (el interceptor de M8 ya
   audita el request; el log hace visible el estado en la operación).
3. `GET /pasarela` suma el campo `bypassPermitido` (false en `prod`) para que el frontend deshabilite
   el toggle con una explicación, en vez de mostrar un error después del click.
4. **Frontend:** mientras la pasarela esté apagada, un **banner persistente** en todo `/admin`
   ("Modo Bypass activo: las reservas se confirman sin cobro"). Hoy el estado solo se ve en la
   pantalla de salud.

## 4. Tests obligatorios (RED primero)

1. `apagarPasarela_conPerfilProd_409` (test de integración con `@ActiveProfiles({"test","prod"})`
   o `MockEnvironment` inyectado en un test unitario de `PasarelaService`; lo que sea más simple sin
   levantar el validador de arranque — ojo: con `prod` activo, `ArranqueSeguroValidator` aborta si el
   OCR es el stub, así que un contexto completo con `prod` no levanta: preferí el test unitario).
2. `apagarPasarela_fueraDeProd_permitido` (regresión).
3. `reactivarPasarela_conPerfilProd_permitido`.

## 5. Criterios de aceptación

- Suite verde. `REGISTRO_FINDINGS.md` AUD-018 → `CERRADO`.
- `ADR-M5-01.md`: subsección "Actualización" con el ámbito (solo fuera de `prod`) y el control
  compensatorio (banner + log). No borrar el texto original.
- Tasks: T-AUD-018 tildada en los dos archivos.

## 6. NO tocar

- Las transacciones ya creadas en bypass (`enBypass`): su tratamiento en liberación y reembolso
  ya es correcto (no llaman al proveedor).
