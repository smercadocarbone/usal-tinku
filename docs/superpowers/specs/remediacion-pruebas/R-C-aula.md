# R-C — Aula: mensaje de cierre, avisos y rutas de desarrollo

**Tarea:** T-REM-C · **Rama:** `fix/rem-c-aula` · **Depende de:** nada · Fuente: `HALLAZGOS_aula_en_vivo.md` §4

## C-1 Cierre sin mensaje tras un corte del backend (H-AULA-1)

**Qué pasa (reproducido, `docs/operacion/pruebas_aula/capturas/L2-C-estudiante-corte.png`):** cuando el backend
corta la sesión (rama adultos "Sí", corte pedido por API, corte automático) y el cliente no recibió el mensaje
de datos `corte-seguridad` del otro navegador, `RoomEvent.Disconnected` cae en `setEstado("esperando")`:
la pantalla dice "Esperando al otro participante" con el botón "Finalizar clase", como si el otro se hubiera ido.

**Precisión importante:** que **la otra persona salga** (por ejemplo, perdió la protección y su navegador cortó,
escenario E) **sí** es "esperando al otro participante": esa persona puede volver a entrar. El defecto es solo
cuando **la sesión quedó cerrada en el backend**.

**Arreglo:** que el cierre dependa del estado de la sesión en el backend, no de un aviso entre pares.

Backend (`backend/src/main/java/com/tinku/aula/`):
1. `EstadoKillswitchResponse` suma dos campos: `sesionCerrada` (estado `finalizada`, `finalizada_anticipada` o
   `interrumpida`) y `cortePorSeguridad` (existe una Alerta de la sesión). `SesionService.estadoKillswitch`
   los calcula con `alertaRepo.findBySesionId(sesionId).isPresent()` y el estado de la sesión.
2. **No** se expone nada de la Alerta (BR-KS-03): solo el booleano, que los participantes ya conocen por el aviso.

Frontend (`frontend/src/app/aula/[id]/page.tsx`, handler de `RoomEvent.Disconnected`, línea ~845):
3. Después de `if (orquestadorRef.current?.alDesconectar()) return;`, **antes** de caer en "esperando", llamar
   a `getEstadoKillswitch(sesionId)` con hasta 3 reintentos separados por 1 s (la sala se cierra un instante
   antes de que el estado se persista):
   - `cortePorSeguridad` → `setCierreKillswitch("seguridad")`;
   - `sesionCerrada` sin corte → ir a la pantalla de clase finalizada que ya existe (`setEstado("finalizada")`);
   - ninguno de los dos → `setEstado("esperando")` como hoy.
4. Los dos casos de la rama adultos "Sí" ahora muestran "La clase se cortó por seguridad" al detectado también.

**Tests (RED):**
- Backend, en `KillswitchClienteIntegracionTest`: `estadoKillswitch_trasCorte_cortePorSeguridadTrue_sesionCerradaTrue`;
  `estadoKillswitch_sinCorte_ambosFalse`; `estadoKillswitch_claseFinalizadaSinAlerta_sesionCerradaTrueCorteFalse`.
- Frontend, `frontend/tests/aula/killswitch-cierre.spec.ts` (API mockeada): simular `Disconnected` con
  `GET /killswitch` devolviendo `cortePorSeguridad:true` → aparece "La clase se cortó por seguridad";
  con `sesionCerrada:true` y sin corte → pantalla de finalizada; con ambos `false` → "Esperando al otro participante".
  Para simular `Disconnected` sin LiveKit, extraé el handler a una función pura
  `resolverCierre(estado: EstadoKillswitch): "seguridad" | "finalizada" | "esperando"` en
  `src/lib/killswitch/cierre.ts` y testeala directo (tabla de 4 casos), más un test de integración de la página
  con `room.disconnect()` si el patrón de `killswitch-entrada.spec.ts` lo permite.

## C-2 El cartel "Verificando la protección…" dura 6 s aunque el video ya volvió (H-AULA-2)

`ui.aviso` en la página arma un `setTimeout(..., 6000)` y `alNormalizar` del orquestador no lo borra.
1. Agregar a `DepsOrquestador.ui` (`frontend/src/lib/killswitch/orquestador.ts`) `limpiarAviso(): void`.
2. `alNormalizar()` y `alReactivar()` llaman a `ui.limpiarAviso()` además de `mostrarTodoRemoto()`.
3. En la página: `limpiarAviso: () => { clearTimeout(temporizadorAvisoRef.current); setAvisoKillswitch(null); }`.
**Tests (RED) en `killswitch-orquestador.spec.ts`:** `KS-048` `alDemorar` y después `alNormalizar` → llamadas
`ocultarTodoRemoto`, `aviso("Verificando…")`, `mostrarTodoRemoto`, `limpiarAviso`; `KS-049` `alCaer` y después
`alReactivar` → termina en `limpiarAviso`.

## C-3 Título casi invisible en la pantalla de corte (H-AULA-3)

`frontend/src/components/aula/AvisoProteccion.tsx`: el título ("La clase se cortó por seguridad") usa un color
oscuro sobre el fondo oscuro de la pantalla; el texto de abajo sí se lee. Usar el mismo token de texto claro que
el cuerpo. **Test (RED):** con `@axe-core/playwright` (ya es dependencia) renderizar `AvisoProteccion` en los 3
motivos y exigir **0 violaciones de `color-contrast`**; el RED es la violación actual.
Página de prueba: reutilizá `src/app/dev/killswitch/` agregando un selector de motivo, o un test de componente
si `tests/` ya tiene un patrón (`rg "dev/componentes" frontend/tests`).

## C-4 `/dev/*` responde 200 en producción (H-AULA-4)

`https://tinku.site/dev/killswitch` y `/dev/componentes` devuelven 200 con "Cargando…" y el título de la página
de desarrollo; el componente hace `notFound()` pero el shell ya salió con 200.
Arreglo: en el `proxy.ts` de Next (el que protege rutas por cookie; `rg -l "proxy" frontend/src --glob "proxy.ts"`)
agregar, antes de cualquier otra lógica:
```ts
if (process.env.NODE_ENV === "production" && pathname.startsWith("/dev")) {
  return new NextResponse(null, { status: 404 });
}
```
**Test:** `frontend/tests/dev-rutas.spec.ts` contra `next build && next start` (no contra `next dev`):
`GET /dev/componentes` y `/dev/killswitch` → 404. Si el pipeline de Playwright solo corre en dev, dejá el test
con `test.skip(process.env.NODE_ENV !== "production")` y documentá cómo correrlo en el commit.

## C-5 Medir la demora de 3 s en un dispositivo real (de H-AULA-2)

No es código: queda en `M3C-07` (página `/diagnostico/clasificador`). Si el p95 de una clasificación en gama media
supera 1 s, proponer al dueño un cambio de la fila "Demora que oculta el video con un menor" de la Tabla de
Tiempos (no cambiarla sola).

## Verificación del chunk

- Backend `./mvnw -B test` (JDK 21) y frontend `npm run lint && npm run build && npx playwright test tests/aula`.
- Repetir `docs/operacion/pruebas_aula/scripts/live2.mjs` (escenarios C y E) en un entorno de pruebas:
  C-12 y E-7 ya no deben mostrar "Esperando al otro participante" cuando la sesión está cerrada.
- Marcar cerrados H-AULA-1 a H-AULA-4 en `HALLAZGOS_aula_en_vivo.md`; tildar T-REM-C.

## NO tocar

- Las ramas del orquestador ni los umbrales (ADR-M3-07).
- `corte-seguridad` entre pares: se mantiene como aviso rápido; el estado del backend es la fuente de verdad.
