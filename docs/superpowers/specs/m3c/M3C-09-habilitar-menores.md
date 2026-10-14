# M3C-09 — Precondiciones y encendido de las clases con menores en producción

**Tarea:** T-M3-06i · **Branch:** `chunk/m3-c` (documentación) · **Riesgo:** **el más alto del proyecto** (Artículo II) · **Depende de:** M3C-02 a M3C-08 y M3C-10 · **Decide:** el dueño del producto

## 1. Problema

`tinku.menores.sesiones-habilitadas` (`TINKU_MENORES_SESIONES_HABILITADAS`) solo pasa a `true` con
T-M3-06 y T02 cerradas (AGENTS §3, T-TES-10). T02 ya está cerrada. Esta spec junta todo lo que tiene
que ser verdad antes de encenderla, para que la decisión no dependa de la memoria de nadie.

**El agente nunca enciende la flag.** La enciende el dueño, en Coolify, después de revisar esta lista.

## 2. Lista de precondiciones

### A. T-M3-06 cerrada

- [x] M3C-01: ADR-M3-07 aceptado y tiempos en la Tabla (2026-10-14).
- [ ] M3C-02 a M3C-05 mergeadas a `main`.
- [ ] M3C-06: E2E del kill-switch verde (los 9 casos), con la salida adjunta, y suite del backend
      verde con JDK 21 (≥ baseline).
- [ ] M3C-07: latencia medida con el código real. La latencia estimada de detección con 1 track, a 4×
      y wasm, es ≤ 3 s, o el dueño aceptó explícitamente el número.
- [ ] `Spec_M3`: FR-AULA-003, FR-AULA-004 y FR-AULA-010 y US-6 sin marcas de "NO IMPLEMENTADO".
- [ ] T-M3-06 tildada en `Tasks_Tinku_Implementacion.md` y el Chunk M3-C en `[x]` en
      `Tasks_Tinku_Chunks.md`, en el mismo commit.
- [ ] Las dos filas "propuesto 2026-10-14" de la Tabla de Tiempos (carga máxima de 20 s y 3 s sin
      respuesta) confirmadas por el dueño, quitando la marca.

### B. Hallazgos de las pruebas en producción que tocan a menores

De `docs/operacion/HALLAZGOS_pruebas.md`. Los cuatro se arreglan en **M3C-10**:

- [ ] **T3-01 (alta):** un menor puede cambiar sus propias capacidades con
      `PATCH /api/usuarios/me/capacidades`. Arreglo con test de regresión (403 si es menor) y
      restauración de Benjamín `50000002`.
- [ ] **T4-H1 (alta):** la cola de CAP da 403 apenas hay un CAP pendiente. Sin esa cola no se pueden
      revisar los antecedentes de los Tutores que darían clases a menores.
- [ ] **T3-03 (media):** el menor abre pantallas de otros roles en la UI. La API ya protege, pero
      conviene cerrarlo antes de tener menores reales usando la app.
- [ ] **TRANS-1 (media):** errores de validación devueltos como 403. Si no, un error del kill-switch o
      de la evidencia se ve como un problema de permisos y se pierde.

### C. Operación

- [ ] **FR-SEC-013:** plazo de retención extendida del clip en un caso escalado por contenido ilegal,
      "a definir con asesoría legal antes del piloto" (`Spec_M9`). Hoy no está en la Tabla de Tiempos.
      Es una decisión legal, no técnica.
- [ ] Al menos un Tutor real con CAP aprobado y vigente revisado por el admin de Moderación desde la
      cola (no por `PATCH` directo).
- [ ] El admin de Moderación sabe resolver una Alerta de kill-switch dentro de la ventana de 12 hs
      (Tabla de Tiempos) y tiene las notificaciones encendidas.
- [ ] Un segundo admin de Moderación (hallazgo T4-H3), o una regla escrita de qué pasa si el único
      admin es parte de un caso.

## 3. Encendido (lo hacés vos)

1. Coolify → recurso de producción → *Environment Variables* →
   `TINKU_MENORES_SESIONES_HABILITADAS=true` → redeploy.
2. Verificar con una cuenta de prueba de un menor (no un menor real): una reserva con un Tutor con CAP
   aprobado **no** da 409 `MENORES_PILOTO`, y una con un Tutor sin CAP sigue dando 409.
3. Dejar registro de la fecha en `Tasks_Tinku_Implementacion.md` (T-TES-10).
   - Si la línea de AGENTS §3 sobre la flag tiene que cambiar, AGENTS §8 dice que va junto con la
     enmienda de la Constitución que corresponda: preguntar antes de editarla sola.

## 4. Vuelta atrás

- Poner la variable en `false` y redeployar corta las **reservas nuevas** con menores (los puntos de
  corte son `ReservaService.crearReserva` y `SolicitudService.crear`).
- **No cancela las ya confirmadas.** Si la vuelta atrás es por un problema del kill-switch, hay que
  cancelar a mano esas clases desde Soporte Financiero, con reembolso.
- Antes de encender, conviene tener escrita la consulta SQL que las lista.

## 5. NO tocar

- El default de la property en `application.yml`: sigue `false`. Se enciende solo por variable de
  entorno.
