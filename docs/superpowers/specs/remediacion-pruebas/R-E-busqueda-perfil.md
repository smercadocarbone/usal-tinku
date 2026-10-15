# R-E — Búsqueda y perfil público: relevancia, búsquedas guardadas y comentarios

**Tarea:** T-REM-E · **Rama:** `fix/rem-e-busqueda` · **Depende de:** nada · **Decisiones:** D-3 (relevancia), D-4 (comentarios)
Fuente: `hallazgos_parciales/T1.md` (1, 6, 11, 12).

## E-1 Relevancia de la búsqueda (T1-M1) — Dec D-3

**Qué pasa:** "clases de tango bailarín" devuelve 17 tutores (Física, Química, Psicología…), "asdkjh qwezxc" devuelve 3
y "a" devuelve 24, con puntajes entre 0,34 y 0,47. La UI dice "Encontramos 17 tutores para ayudarte con…" y nunca
llega al estado "Todavía no encontramos tutores…". Contradice US-1 de `Spec_M2`.

**Causa confirmada** (`MatchingOrquestador.buscar`, `AjusteRankingService.ajustar`): el corte de relevancia es **un
umbral absoluto** (`tinku.matching.score-minimo`, `MATCHING_SCORE_MINIMO`, 0,3). Con el modelo de embeddings que usa
el servicio, hasta un texto sin sentido tiene similitud 0,34–0,47 con cualquier tutor, así que pasa. Si `directos` no
queda vacío, nunca se activa la recomendación por área (`RecomendacionPorAreaService`) ni el estado vacío.

**Paso 1 — experimento (no cambia código de producción):** `matching-service/tests/eval_relevancia/`
1. `consultas.csv` con ≥ 40 filas `texto,esperado` armadas **con el catálogo del propio seed** (materias de
   `TutorSeedRunner`: matemática, inglés, física, programación, historia, química, psicología…):
   - 20 **relevantes** ("ayuda con derivadas", "inglés para viajar", "programación en Python");
   - 10 **irrelevantes con sentido** ("clases de tango", "cómo cocinar milanesas", "jardinería");
   - 10 **sin sentido** ("asdkjh qwezxc", "a", "zzzz", "!!!!").
   Nada sensible: son consultas inventadas.
2. Script `evaluar.py` que corre cada consulta contra el matching-service real (el del `docker-compose`), con
   el seed cargado, y guarda `score_top1`, `score_top5` y `n_resultados` por consulta en `resultados.csv`.
3. Informe `docs/operacion/CALIBRACION_busqueda.md` con la distribución de `score_top1` para cada grupo y una
   tabla de qué umbral absoluto y qué margen relativo separan mejor los grupos.

**Paso 2 — regla nueva (D-3 recomendada):** además del umbral absoluto, un **corte relativo al mejor resultado**:
`score >= max(umbralAbsoluto, top1 − margen)`. Ambos valores salen del informe y van a variables de entorno
(`MATCHING_SCORE_MINIMO`, `MATCHING_MARGEN_RELATIVO`), con el default en `application.yml`. Agregar también una
**validación mínima del texto**: menos de 3 letras o sin ninguna letra → se trata como "sin texto útil" y el
resultado es vacío (la UI muestra el estado vacío con la sugerencia de usar los filtros).
Cambios:
1. `AjusteRankingService.ajustar`: aplicar el corte relativo cuando `exigirRelevancia` es true. El corte para la
   recomendación por área (`exigirRelevancia=false`) no cambia.
2. `InterpreteBusqueda` o el orquestador: la validación del texto.

**Criterio de aceptación (medible):** sobre `consultas.csv`, con el modelo real:
- ≥ 90 % de las relevantes devuelven ≥ 1 resultado directo;
- 100 % de las "sin sentido" y ≥ 80 % de las "irrelevantes con sentido" devuelven **0 resultados directos**
  (caen a la recomendación por área o al estado vacío).
Si no se alcanza con ningún valor, **pará y reportá la distribución al dueño**: puede hacer falta otro modelo
(eso sí exigiría un ADR, AGENTS §2).

**Tests (RED):**
- `AjusteRankingServiceTest`: lista con scores `[0.47, 0.46, 0.45]` y absoluto 0,3 → hoy devuelve los 3; con el
  corte relativo devuelve solo los que están dentro del margen del mejor; lista `[0.9, 0.5]` → solo el primero.
- `MatchingOrquestadorTest` (o el IT existente): texto "a" → resultado vacío; "asdkjh qwezxc" → vacío.
- Playwright `tests/buscar/`: con la API mockeada devolviendo `[]` para un texto, aparece "Todavía no encontramos
  tutores para enseñarte {texto}" (ya existe en `buscar/page.tsx:493`; el test fija el comportamiento).

## E-2 Búsquedas guardadas (T1-6)

**Qué pasa:** `matching.busquedas_guardadas` solo tiene `texto_busqueda` (V7): los filtros (nivel, materia,
precio máximo, orden) se pierden, se aceptan duplicados y no existe `DELETE`.

1. Migración **`V49__m2_busquedas_guardadas_filtros.sql`** (la siguiente libre; confirmá con
   `ls backend/src/main/resources/db/migration | sort -V | tail -1`):
   ```sql
   ALTER TABLE matching.busquedas_guardadas
       ADD COLUMN filtros JSONB NOT NULL DEFAULT '{}'::jsonb,
       ADD COLUMN huella  VARCHAR(64);
   -- huella = sha256(texto normalizado + filtros ordenados); se completa en la aplicación.
   UPDATE matching.busquedas_guardadas SET huella = md5(lower(texto_busqueda)) WHERE huella IS NULL;
   CREATE UNIQUE INDEX uq_busquedas_guardadas_usuario_huella
       ON matching.busquedas_guardadas(usuario_id, huella);
   ```
   Antes del índice único, la migración tiene que **deduplicar** las filas existentes (conservar la más vieja de
   cada `usuario_id, huella`): agregá un `DELETE ... USING` previo.
2. `BusquedaGuardada` (entidad) y `BusquedasGuardadasService.guardar`: recibir y guardar `filtros`; calcular la huella;
   si ya existe, devolver la existente (200 en vez de 201) sin duplicar.
3. `POST /api/busquedas/guardadas/{id}/ejecutar`: pasar los filtros guardados a `orquestador.buscar(...)`.
4. `DELETE /api/busquedas/guardadas/{id}` → 204; 404 si no es del usuario (mismo criterio que B-4).
5. `GuardadaResponse` suma `filtros`. Front: `guardarBusqueda` (`frontend/src/lib/api.ts`) manda los filtros
   activos; la lista de guardadas muestra un chip por filtro y un botón "Borrar".
**Tests (RED):** `BusquedasGuardadasIntegracionTest`: guardar con filtros → ejecutar usa los filtros (el resultado
coincide con la búsqueda directa con esos filtros); guardar dos veces lo mismo → una sola fila; `DELETE` propio
204 y ajeno 404; guardada sin filtros sigue funcionando. Playwright: botón "Borrar" llama al `DELETE`.

## E-3 Comentarios públicos en el perfil del Tutor (T1-11, FR-REP-001) — Dec D-4

**Spec dice** (`Spec_M7` FR-REP-001): la calificación del Estudiante al Tutor es pública, "visible en el perfil del
Tutor", con comentario opcional. Hoy el perfil muestra promedio y cantidad pero ningún comentario y no existe un
endpoint que los liste.

Con la decisión recomendada:
1. Backend: `GET /api/tutores/{tutorId}/calificaciones?limite=5` (autenticado, como el resto del perfil) →
   `[{ "estrellas": 5, "comentario": "…", "fecha": "2026-10-01", "contexto": "Familia de nivel secundario" }]`.
   - Solo `direccion = 'estudiante_a_tutor'`, con comentario no vacío, **no ocultas por moderación** (buscá el
     campo con `rg "oculta" backend/src/main/java/com/tinku/reputacion`; los comentarios ocultos que ve
     `/api/admin/moderacion/calificaciones-ocultas/{id}` tienen que quedar fuera).
   - **Sin nombre ni id del alumno ni del pagador** (el menor nunca aparece, Artículo II). `contexto` sale del
     nivel de la clase si está disponible; si no, se omite.
   - **Solo si el Tutor ya muestra promedio** (≥ 5 calificaciones públicas, FR-REP-007); si no, devuelve `[]`.
2. Front `app/tutores/[id]/page.tsx`: sección "Lo que cuentan las familias" con los comentarios (texto
   escapado, nunca HTML); oculta si la lista es vacía.
**Tests (RED):** IT: Tutor con 6 calificaciones (3 con comentario, 1 oculta) → devuelve 2; con 4 calificaciones →
`[]`; el JSON no contiene `autorId`, `pagadorId`, `nombre` ni `apellido`. Playwright: un comentario con
`<script>` se muestra como texto.

## E-4 Filtros de la búsqueda (T1-12)

1. `filtro_materia` por API distingue mayúsculas y tildes (`"matematica"` no encuentra "Matemática"): normalizar en
   `PerfilTutorTemasRepository` o en el servicio con una función `normalizar(String)` (minúsculas + quitar
   diacríticos con `java.text.Normalizer`) aplicada a los dos lados de la comparación. La UI usa chips y no se ve afectada.
2. Precio máximo `-5` en la UI: el campo no acepta valores < 1 y muestra "Ingresá un precio mayor a cero" en vez
   de quitar el signo en silencio (`frontend/src/app/buscar/page.tsx`, buscá el manejo de `precioMax`).
**Tests (RED):** IT: `filtro_materia:"matematica"`, `"MATEMÁTICA"` y `"Matemática"` devuelven lo mismo. Playwright:
escribir `-5` muestra el mensaje y no dispara la búsqueda.

## Verificación del chunk

- Backend `./mvnw -B test`; matching-service `uv run pytest` (o el comando de su README); frontend completo.
- `docs/operacion/CALIBRACION_busqueda.md` commiteado con los números.
- Repetir contra producción las 3 consultas de T1 ("clases de tango bailarín", "asdkjh qwezxc", "a").
- Marcar cerrados: T1-M1, T1-6, T1-11, T1-12 (filtros); tildar T-REM-E.

## NO tocar

- La recomendación por área (`RecomendacionPorAreaService`, FR-MATCH-011) y su umbral propio
  (`score-minimo-area`).
- La sombra por mala calificación (BR-MATCH-01).
