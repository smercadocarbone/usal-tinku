# Plan de remediación de los hallazgos de las pruebas en producción

> Fuentes: `HALLAZGOS_pruebas.md` (testers T1–T7, 2026-10-14), `HALLAZGOS_aula_en_vivo.md` (aula y
> kill-switch) e informes parciales en `hallazgos_parciales/`. Este plan cubre **todos** los hallazgos:
> cada uno termina en un arreglo con test, una decisión tuya, una acción operativa, o una aceptación
> explícita (§6). Las specs que ejecuta el modelo implementador están en
> `docs/superpowers/specs/remediacion-pruebas/` (más `m3c/M3C-10-hallazgos-menores.md`, que ya existe).

**Versión 2 (2026-10-15).** Cambios respecto de la primera versión:
- las decisiones de §4 quedan **adoptadas** (el dueño delegó la elección en la recomendación);
- estrategia de ramas apiladas y orden de ejecución definitivo (§5);
- registro de riesgos y vuelta atrás por chunk (§5.1);
- plan B para la calibración de la búsqueda cuando el modelo de embeddings no puede levantarse (§5.2);
- entorno de verificación: la suite del backend corre con Testcontainers, así que necesita Docker (§2.5).

## 1. Estado de partida y qué manda

- **0 bloqueantes.** Hay 3 hallazgos altos (T3-01, T4-H1, T2-H1) y un patrón transversal (TRANS-1).
- **Lo urgente no es de código:** producción está hoy con el bypass de pago encendido (§3, O-1).
- La **seguridad del menor** (Artículo II) va primero; después lo que rompe un flujo; después UX.

## 2. Para el modelo que implementa

1. Leé `AGENTS.md` entero y este plan. Después trabajá **un chunk por vez**, en el orden de §5, en una
   rama propia `fix/{chunk}` (AGENTS §7). Nunca directo a `main`; nunca `--amend` ni force-push.
2. En cada ítem: **primero el test que reproduce el hallazgo y falla (RED)**, después el arreglo
   (GREEN). Pegá la línea del fallo RED en el mensaje del commit (AGENTS §9).
3. **No reabras decisiones.** Las decisiones de §4 que figuran como "recomendada" son las que asumen las
   specs. Si el dueño cambió alguna, la spec afectada lo dice al principio.
4. Los plazos salen de `Tabla_Tiempos_Tinku.md`; migraciones nuevas desde `V48__` (hoy la última es `V47`); no se
   editan las aplicadas; eventos de dominio: ninguno nuevo (AGENTS §4). Migraciones: R-F `V48`, R-E `V49` (se invirtió al implementar: F se mergea antes que E en la cadena de ramas y Flyway aplica en orden de versión).
   Si dos ramas en paralelo toman el mismo número, **la segunda en mergear renumera la suya** antes de mergear.
5. Verificación antes de cada commit:
   - Backend, desde `backend/`: `JAVA_HOME=<jdk21> ./mvnw -B test`. **Leé la línea `Tests run:`**: nunca
     afirmes que pasa por el exit code. Baseline al cerrar M3-C: ≥ 661 tests, 0 failures, 0 errors;
     cobertura ≥ 85,6 %.
   - Frontend, desde `frontend/`: `npm run lint`, `npm run build`, `npx playwright test`.
   - **Docker:** los tests de integración levantan `pgvector/pgvector:pg16` con Testcontainers. En un entorno
     sin daemon: `dockerd &` y exportar `TESTCONTAINERS_RYUK_DISABLED=true` (evita bajar la imagen de Ryuk).
6. Al cerrar un chunk: tildá su tarea (`T-REM-*`) en `Tasks_Tinku_Implementacion.md` **y** actualizá
   `Tasks_Tinku_Chunks.md` en el mismo commit (AGENTS §8); marcá los hallazgos cerrados en
   `HALLAZGOS_pruebas.md` / `HALLAZGOS_aula_en_vivo.md` con el commit.
7. Si el código real no coincide con lo que dice una spec, **pará y preguntá**.

## 3. Ola 0 — mitigación inmediata (sin código; la hace el dueño)

| # | Acción | Por qué | Cómo se verifica |
|---|--------|---------|------------------|
| **O-1** | **Cerrar el bypass de pago.** Con el admin financiero: `PATCH /api/admin/financiero/pasarela {"habilitada": true}`. **Después** volver `TINKU_PERFILES` a `prod` (o `prod,seed-demo` mientras haya pruebas) en Coolify y redeployar | Hoy `habilitada=false` está **guardado en la base** y el modo bypass (`PagoService`) confirma reservas sin cobrar a **cualquier** usuario real. Volver al perfil `prod` **no** lo cierra: el flag vive en `pagos.pasarela_estado`. Además, sin el perfil `prod`, `ArranqueSeguroValidator` está más permisivo | `GET /api/admin/financiero/pasarela` → `habilitada:true, bypassPermitido:false`; `POST /api/pagos/preferencia` sobre una reserva nueva devuelve `bypass:false` y un `initPoint` |
| O-2 | Si querés seguir probando con bypass, hacelo en el **entorno de pruebas** (`M3C-08`, perfil `staging`), no en `tinku.site` | Mismo motivo | `RUNBOOK_staging.md` |
| O-3 | **Cambiar la contraseña de los admins** `99900301` y `99900302` | La clave `UnaClaveLarga-2026` circuló en el chat de las pruebas | Login con la clave vieja falla |
| O-4 | Antes del piloto real: sacar `seed-demo` de `TINKU_PERFILES` | Contraseña conocida (`password123`) en 60 cuentas | Login de `40000001` da 401 |
| O-5 | Decidir si la flag de menores sigue encendida: `TINKU_MENORES_SESIONES_HABILITADAS`. Hoy está en `true` | `M3C-09` exige antes M3C-10 (T3-01, T4-H1, T3-03, TRANS-1) y definir FR-SEC-013. Con el pago real activo, un menor real podría reservar | Una reserva con un menor da 409 `MENORES_PILOTO` si la apagás |
| O-6 | Correr `deploy/demo/limpiar-residuos-pruebas-2026-10-14.sql` (viene en `ROLLBACK`; mirá los conteos y pasalo a `COMMIT`) | Restaura a Benjamín (T3-01), borra la franja de 2020, cierra las 2 denuncias huérfanas y las búsquedas guardadas | Los conteos del script |
| O-7 | **Nombrar un segundo admin de Moderación** (`INSERT INTO admin.admins`, RUNBOOK §2.5) | Con uno solo, una denuncia donde es parte queda sin salida (T4-H3). Mitigación sin código | Un admin distinto puede resolver una denuncia del otro |
| O-8 | **Cargar los precios regionales** (`/admin/precios`) o confirmar que está vacío a propósito | `precios-regionales` devuelve `[]`: el Tutor no tiene referencia de precio (T5-H4) | `GET /api/pagos/precio-referencia/{provincia}` devuelve un valor |
| O-9 | HSTS de `api.tinku.site`: en Cloudflare, *SSL/TLS → Edge Certificates → HSTS* sobre la zona (cubre el subdominio) | T6: falta la cabecera en la API. Se arregla en el borde, no en el código | `curl -I https://api.tinku.site/actuator/health` trae `strict-transport-security` |
| O-11 | Correr la calibración de la búsqueda si E-1 quedó en plan B (§5.2): `matching-service/tests/eval_relevancia/evaluar.py` contra staging o producción | El experimento necesita el modelo real | `CALIBRACION_busqueda.md` con números |
| O-10 | Medir la latencia del clasificador en un celular real (página `/diagnostico/clasificador`, `M3C-07`) | Los números son de un Chromium con la CPU frenada | Anexo de ADR-M3-01 |

## 4. Decisiones de producto — **adoptadas el 2026-10-15**

> El dueño pidió ejecutar sin consultarle: se adopta la recomendación de cada fila. D-8 (PWA) queda en "no"
> para el piloto; solo se hacen los íconos y el `favicon`, que no tienen riesgo. Cualquier cambio posterior
> del dueño se hace como un chunk nuevo, no reescribiendo estos.


| # | Decisión | Adoptada | Afecta |
|---|----------|-------------|--------|
| **D-1** | **Baja de franjas del Tutor** (hueco del Spec M4, T2-H1): ¿qué pasa con las clases ya reservadas dentro de la franja? | Quitar una franja es una **baja lógica** (`activa=false`) que no toca las reservas ya hechas: esas clases se mantienen. La pantalla avisa cuántas hay | R-D |
| D-2 | **Franjas contiguas** (T1-M2): un Tutor con 15 franjas de 1 h seguidas no puede ofrecer clases de más de 1 h | Unir las franjas contiguas **al calcular horarios y validar reservas**, sin cambiar lo guardado | R-D |
| **D-3** | **Relevancia de la búsqueda** (T1-M1): "clases de tango bailarín" devuelve 17 tutores | Calibrar el umbral con un set de consultas del propio catálogo y pasar a un corte **relativo al mejor resultado** además del absoluto. Sin respuesta, el chunk R-E se detiene en el experimento | R-E |
| D-4 | **Comentarios públicos en el perfil** (FR-REP-001, T1-11): hoy solo se ve el promedio | Mostrar los últimos 5 comentarios, **sin nombre del alumno** ("Familia de nivel secundario"), y solo cuando el Tutor ya muestra promedio (≥ 5 calificaciones, FR-REP-007) | R-E |
| D-5 | **Denuncia donde el admin es parte** (T4-H3): ¿hace falta una función de derivación o alcanza con un segundo admin? | Alcanza con O-7. En código: marcar la denuncia como `conflicto` y devolver un mensaje claro; sin flujo de reasignación | R-F |
| D-6 | **Segundo descargo** (T4-H8): hoy el segundo pisa al primero | Conservar los dos (se agregan con fecha, 300 caracteres cada uno) | R-F |
| D-7 | **Contacto en la bio del Tutor** (T2-H11): hoy se publica email o teléfono y se modera después | Enmascarar automáticamente emails y teléfonos al guardar, y avisar al Tutor. Mantener la moderación posterior | R-B |
| D-8 | **PWA** (T7): service worker e íconos | Fuera del piloto; se hace en la última ola solo si lo pedís | R-G (opcional) |
| D-9 | **CSP** (T6): sin `Content-Security-Policy` en el front | Primero en modo `Report-Only` durante una semana, después se aplica | R-G |

## 5. Olas y chunks

| Ola | Chunk | Spec | Tarea | Depende de | Decisión |
|-----|-------|------|-------|------------|----------|
| 1 | **A** — Seguridad del menor y errores 403 | `m3c/M3C-10-hallazgos-menores.md` | T-M3-06j | — | — |
| 2 | **B** — Robustez de la API | `R-B-api-robustez.md` | T-REM-B | A (TRANS-1) | D-7 |
| 2 | **C** — Aula: cierre y avisos | `R-C-aula.md` | T-REM-C | — | — |
| 2 | **D** — Agenda y franjas | `R-D-agenda-franjas.md` | T-REM-D | — | D-1, D-2 |
| 2 | **E** — Búsqueda y perfil público | `R-E-busqueda-perfil.md` | T-REM-E | — | D-3, D-4 |
| 2 | **F** — Admin y moderación | `R-F-admin-moderacion.md` | T-REM-F | A, B | D-5, D-6 |
| 3 | **G** — Frontend: móvil, accesibilidad y cabeceras | `R-G-frontend-a11y.md` | T-REM-G | B (para `rolAdmin`) | D-8, D-9 |
| 3 | **H** — Datos del seed | `R-H-seed.md` | T-REM-H | D | — |

Los chunks de una misma ola son independientes entre sí y pueden ir en paralelo (ramas distintas).
Orden recomendado dentro de la ola 2: **D** (rompe flujos), **B**, **C**, **F**, **E**.

### Estrategia de ramas (definitiva)

Ramas **apiladas**, una por chunk, en el orden de ejecución: `fix/rem-a` sale de `main` con el plan,
`fix/rem-d` sale de `fix/rem-a`, y así: **A → D → B → C → F → E → G → H**. Cada rama compila y deja la suite
verde. Para integrar alcanza con mergear la última (`fix/rem-h`), que contiene todo, o ir mergeando en orden.
El orden pone primero lo que afecta a menores (A) y lo que rompe un flujo (D); B va antes de F y G porque
estos usan `rolAdmin` de `/api/usuarios/me` (B-10).

### 5.1 Riesgos y vuelta atrás

| Chunk | Riesgo principal | Mitigación | Vuelta atrás |
|---|---|---|---|
| A | Permitir el dispatch `ERROR` abre algo que antes negaba | Test `rutaProtegida_sinJwt_sigue403`; `/error` solo muestra status y mensaje | Revertir el commit de `SecurityConfig` |
| B | Mensajes que el front compara por texto | El front decide por `codigo` (R5); buscar textos con `rg` antes de cambiar | Revertir el commit del ítem |
| C | Llamar al backend en `Disconnected` agrega latencia al cierre | Hasta 3 reintentos de 1 s; si falla, cae en "esperando" como hoy | Revertir el commit del front |
| D | Unir franjas cambia qué horarios se ofrecen y validan | Tests de límites y regresión de AUD-025; la base sigue impidiendo superposiciones (V30) | Revertir; los datos no cambian (sin migración) |
| E | El corte relativo puede ocultar resultados válidos | Criterio medible sobre un set de consultas; valores en variables de entorno | `MATCHING_MARGEN_RELATIVO=1` desactiva el corte relativo sin redeploy de código |
| F | Guard por rol en la cadena de filtros niega algo legítimo | Regresión sobre todos los IT de admin | Revertir el commit de `SecurityConfig` |
| G | CSP rompe recursos en silencio | Solo `Report-Only`; nunca se pasa a obligatoria en este chunk | Sacar la cabecera de `next.config.js` |
| H | El seed nuevo no aplica a bases ya sembradas | Documentado; es idempotente | No aplica |

### 5.2 Plan B de la calibración de la búsqueda (E-1)

El experimento necesita el `matching-service` con el modelo de embeddings real. Si no se puede levantar en el
entorno de trabajo (sin acceso al modelo, sin memoria), se implementan igual las dos protecciones que **no
dependen** de calibrar: la validación mínima del texto ("a", "asdkjh qwezxc" → vacío por regla) y el corte
relativo con un margen conservador por defecto (`0,10` del mejor puntaje). El informe
`CALIBRACION_busqueda.md` queda con el set de consultas y el script listos, y el dueño corre la calibración
contra producción o staging (acción O-11).

## 6. Trazabilidad: cada hallazgo y qué se hace con él

Leyenda: **Fix** = arreglo con test · **Dec** = espera una decisión de §4 · **Op** = acción de §3 ·
**Acepto** = se deja como está, con la razón.

### Altos y transversales
| Hallazgo | Qué se hace | Chunk |
|----------|-------------|-------|
| T3-01 un menor cambia sus capacidades | Fix + Op O-6 | A (H-1) |
| T4-H1 cola de CAP da 403 | Fix | A (H-2) |
| T2-H1 el Tutor no puede quitar franjas | Fix, Dec D-1 | D |
| TRANS-1 errores de validación como 403 vacío (T1-4, T2-H4, T3-04, T4-H4, T5-3, T6-19) | Fix | A (H-3), B |

### Medias
| Hallazgo | Qué se hace | Chunk |
|----------|-------------|-------|
| T1-M1 búsqueda devuelve tutores para consultas sin relación | Fix, Dec D-3 | E |
| T1-M2 franjas contiguas no se unen | Fix, Dec D-2 | D |
| T1-M3 `/horarios` con 180 min: duplicados y fuera de franja | Fix | D |
| T2-H2 franja con fecha pasada | Fix | D |
| T2-H3 `diaSemana=9` da 500 | Fix | D |
| T2-H10 perfil público lista franjas pasadas | Fix | D |
| T3-03 el menor abre pantallas de otros roles | Fix | A (H-4) |
| T4-H5 `/admin/salud` pide un endpoint financiero | Fix | F |
| T4-H7 la cola de denuncias no muestra evidencia ni denunciado | Fix | F |
| T4-H3 denuncias donde el admin es parte | Op O-7 + Fix mínimo, Dec D-5 | F |
| T5-H4 `precios-regionales` vacío | Op O-8 + estado vacío en la UI | F |
| T7-M1 `/cuenta/horarios` con scroll horizontal | Fix | G |
| T7-M2 `/cuenta/materias` con scroll horizontal | Fix | G |
| T7-M3 `estado-perfil` pedido a quien no es Tutor | Fix | A (H-4) |
| **H-AULA-1** cierre sin mensaje tras un corte del backend | Fix | C |

### Bajas
| Hallazgo | Qué se hace | Chunk |
|----------|-------------|-------|
| T1-5, T2-H9 mensajes de validación en inglés o sin tildes | Fix | B |
| T1-6 búsquedas guardadas pierden filtros, duplican y no se borran | Fix + Op O-6 | E |
| T1-7, T2-H12, T7 `GET /api/admin/yo` 403 en cada pantalla | Fix | B, G |
| T1-8 el picker rotula "Ocupado" lo que ya pasó | Fix | D |
| T1-9 clase cancelada ofrece "Escribir el pedido" | Fix | D |
| T1-10 cancelar ajena da 403 y verla 404; `confirmar-retorno` da 503 | Fix | B |
| T1-11 comentarios públicos ausentes del perfil (FR-REP-001) | Fix, Dec D-4 | E |
| T1-12 precio máximo `-5`, `filtro_materia` sensible a mayúsculas, mensaje de fecha pasada | Fix | D, E |
| T2-H4 foto de más de 5 MB | Fix (TRANS-1) | A, B |
| T2-H5, T2-H6 totales de Mis cobros en $0 y dos "netos" distintos | Fix (texto aclaratorio) | G |
| T2-H7 el neto se muestra con un precio inválido | Fix | G |
| T2-H8 `updatedAt` viejo y 3 decimales en tarifa | Fix | B |
| T2-H11 contacto en la bio | Dec D-7 | B |
| T3-02 aviso de piloto tarde | **Acepto**: deja de aplicar si la flag de menores se enciende (O-5) | — |
| T3-05 `GET /pagos/tarifa` responde 200 al menor | Fix | B |
| T3-06 solo Lorena tiene CAP en el seed | Fix | H |
| T4-H2 mensaje falso del 403 por conflicto de interés | Fix | F |
| T4-H6 `/admin/pagos` y `/admin/precios` se ven por URL con otro rol | Fix | F |
| T4-H8 el segundo descargo pisa al primero | Fix, Dec D-6 | F |
| T4-H9 "Fundada" preselecciona sanción sin confirmar | Fix | F |
| T4-H10 el admin va a `/cuenta/reservas` al entrar | Fix | F |
| T4-H11 re-rechazar un CAP borra el antecedente | Fix | F |
| T4-H12 400 antes que 403 en endpoints financieros | Fix | F |
| T4-H13 404 sin cuerpo en algunos endpoints admin | Fix | B |
| T5-1 `/admin` visible a Estudiantes y Tutores | Fix | A (H-4) |
| T5-2 acentos rotos en el 403 de admin | Fix | B |
| T6 falta CSP en el front | Fix, Dec D-9 | G |
| T6 falta HSTS en la API | Op O-9 | — |
| T6-4 mensaje "…pueden finalizarla" para un GET | Fix | B |
| T7-5 títulos de pestaña repetidos | Fix | G |
| T7-8 enlaces de 18–23 px de alto | Fix | G |
| T7-9 contraste 3,39:1 en la grilla de horarios | Fix | G |
| T7-10 foco poco visible y orden raro | Fix | G |
| T7-12 dos `h1` en `/cuenta/seguridad` | Fix | G |
| T7-3 150 filas sin agrupar en "Franjas publicadas" | Fix | D, H |
| T7 PWA: service worker, íconos, favicon | Dec D-8 | G (opcional) |
| H-AULA-2 cartel "Verificando…" dura 6 s | Fix | C |
| H-AULA-3 título casi invisible en el corte de seguridad | Fix | C |
| H-AULA-4 `/dev/*` responde 200 en producción | Fix | C |

### Aceptados (no se arreglan)
| Hallazgo | Razón |
|----------|-------|
| T2: "aceptar/rechazar pedidos" no existe | El Spec M4 eliminó la aceptación manual |
| T6: 403 y no 401 sin sesión | Es el comportamiento de Spring Security con filtro JWT; no filtra datos. Cambiarlo toca a todos los clientes |
| T6: franjas de otro Tutor visibles | Son datos públicos del marketplace |
| T1: dos Tutores llamados "María Pérez" | Dato del seed, no un defecto |
| T7-18: "Email" como etiqueta | Es lo común en Argentina |
| T7-13/14: redirecciones silenciosas (`/reservar`, `/cuenta/perfil-tutor`) | Intencionales |
| T7: prefetch `_rsc` y `cdn-cgi/rum` abortados | Ruido de Next.js y de Cloudflare |
| T4-H3 como función de reasignación | Se cubre con O-7 (ver D-5) |

## 7. Criterio de cierre

1. Los 8 chunks cerrados, con sus tareas tildadas en las dos listas y los hallazgos marcados con commit.
2. Suite del backend y de Playwright verdes; cobertura de líneas ≥ 85,6 %.
3. Repetir las pruebas de los testers sobre producción para los hallazgos altos y medios: T3 (menores),
   T4 (cola de CAP), T2 (franjas), T1 (búsqueda y horarios) y T7 (móvil). Se corre un script por
   tester, reutilizando `docs/operacion/pruebas_aula/scripts/` como modelo.
4. La Ola 0 completa, en especial O-1, O-3 y O-5, antes de abrir el piloto a usuarios reales.
