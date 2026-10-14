# M3C-06 — E2E del kill-switch contra un backend y un LiveKit reales

**Tarea:** T-M3-06f · **Riesgo:** medio · **Depende de:** M3C-05; un entorno con backend, LiveKit y bypass (M3C-08 o local, §2) · **Commits:** 2 (E2E y configuración; tabla de trazabilidad)

## 1. Qué hay que lograr

AGENTS §5 pide un test de punta a punta por historia del Spec activo, y que los casos borde de
`Spec_M3` §5 sean tests. Hasta acá:
- el backend tiene tests de integración;
- el cliente tiene tests del orquestador con dependencias falsas.

**Nada prueba que una detección en un navegador termine en una Alerta con clip.** Eso es lo que hace
esta spec.

## 2. Dónde corre

El E2E necesita backend, Postgres, matching, frontend, **LiveKit** y la pasarela en bypass. Dos
opciones; la spec funciona con cualquiera de las dos y el test solo lee variables de entorno.

### Opción A — local con `docker compose` (recomendada para el modelo que implementa)

1. `docker-compose.yml` ya tiene `db`, `matching`, `backend` y `frontend`. Agregá un archivo nuevo
   `docker-compose.e2e.yml` (override, sin tocar el original) con:
   - Servicio `livekit`: `image: livekit/livekit-server:<versión fija>` (buscá la última estable y fijala),
     `command: --dev --bind 0.0.0.0`, puertos `7880` y `7881` y el rango UDP que pida la documentación
     de `--dev`. En modo `--dev` las credenciales son `devkey` / `secret`.
   - En `backend`:
     - `LIVEKIT_URL=ws://livekit:7880` para el backend; `ws://localhost:7880` para el navegador. Si el
       backend devuelve la URL que usa el navegador, configurá la que sirva desde el host.
     - `LIVEKIT_API_KEY=devkey`, `LIVEKIT_API_SECRET=secret`.
     - `SPRING_PROFILES_ACTIVE=dev,seed-demo`.
     - `TINKU_MENORES_SESIONES_HABILITADAS=true`. **Solo en este archivo.**
     - `TINKU_ADMIN_MODERACION_IDS` y `TINKU_ADMIN_FINANCIERO_IDS` con dos DNIs del seed, para que
       `AdminSeedRunner` (perfil `dev`) los promueva. Revisá el nombre real de las variables en
       `application.yml` (`tinku.admin.moderacion.ids` y `tinku.admin.financiero.ids`).
   - El webhook de LiveKit al backend (`LiveKitWebhookController`) hace falta para `joined_at` y el
     no-show. Configurá el `webhook` del server en modo dev apuntando a
     `http://backend:8080/<ruta del controller>` con la misma key. Si no se puede en `--dev`, usá un
     `livekit.yaml` montado.
2. Comando, desde la raíz: `docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build`.
3. Si el entorno donde corre el modelo no tiene Docker, pasá a la opción B y decilo en el resumen.

### Opción B — entorno de pruebas (M3C-08)

`PLAYWRIGHT_BASE_URL=https://staging…` y `E2E_API_URL=https://api.staging…`, con el bypass prendido y
los admins promovidos según el RUNBOOK de staging.

## 3. Configuración de Playwright

1. `frontend/playwright.config.ts`: un proyecto nuevo `e2e-real`:
   - `testMatch: /.*\.e2e\.spec\.ts/`;
   - `use.launchOptions.args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"]`;
   - `timeout: 180_000`.
   El proyecto `chromium` existente agrega `testIgnore: /.*\.e2e\.spec\.ts/`, así el pipeline normal no
   lo corre.
2. Variables: `E2E_API_URL`, y opcionalmente `E2E_TUTOR_DNI` y `E2E_PASSWORD`, con defaults del seed.
3. Script nuevo en `package.json`: `"test:e2e-real": "playwright test --project=e2e-real"`.

## 4. `frontend/tests/aula/killswitch.e2e.spec.ts`

### Helpers en el mismo archivo

- `login(dni)` → JWT, contra `E2E_API_URL/api/usuarios/login`.
- `prenderBypass(jwtFinanciero)` → `PATCH /api/admin/financiero/pasarela { habilitada: false }`.
  Verificá que responda `bypassPermitido: true`; si no, el test falla con un mensaje claro.
- `reservarYPagar(jwtPagador, tutorId, beneficiarioId?)`:
  - crea una reserva en el primer horario libre del Tutor;
  - llama a `POST /api/pagos/preferencia` y confirma que vuelva `bypass: true`, o sea reserva
    confirmada;
  - después, con `GET /api/sesiones/por-reserva/{id}`, obtiene la sesión.
  - La sala se crea a T-5 min (Tabla de Tiempos): elegí un horario que caiga dentro de los próximos
    minutos, o adelantá el job `CrearSalaJob` de esa sesión. **No** cambies el tiempo de la Tabla.
    Si no hay forma limpia, documentá la limitación y usá el horario más cercano posible.
- `dosParticipantes(browser, jwtA, jwtB, sesionId)`: dos `browser.newContext()`, con la sesión
  inyectada como lo hace `tests/helpers.ts` (`setFakeSession`, adaptado a un JWT real). Los dos abren
  `/aula/{sesionId}` y entran.
- `forzarDeteccion(page)`: no hay modelo falso en el build real. **Opción A:** el frontend corre con
  `npm run dev` y se inyecta con `page.addInitScript` un `window.__tinkuModeloFalso` que devuelve
  `Porn: 0.99` desde que se setea `window.__tinkuForzar = true`. **Opción B:** si staging corre un
  build de producción, este E2E no se puede correr ahí; usá la opción A.
- Verificación con el admin de Moderación: `GET /api/admin/moderacion/alertas` → la Alerta de esa
  sesión, con `rama` y `tieneClip`/clip presente.

### Casos

| ID | Historia / caso borde | Actores | Pasos | Verificación |
|---|---|---|---|---|
| E2E-KS-01 | US-6, Tutor detectado | AR `40000012` paga para Mía `50000001` con Tutor `31000028` (tiene CAP) | Entran Tutor y Mía; `forzarDeteccion` en el contexto de **Mía** | En los dos navegadores aparece "La clase se cortó por seguridad". La Alerta tiene `rama=menor`, `detectadoId` = Tutor y clip. La sesión queda `finalizada`. Escrow `pausado_alerta` (consulta con el admin financiero, o por SQL si no hay endpoint, documentado). Hay una notificación `KILLSWITCH_MENOR` para el AR |
| E2E-KS-02 | US-6, menor detectado (caso borde 4) | Igual | `forzarDeteccion` en el contexto del **Tutor** | Corte para los dos; Alerta con `detectadoId` = Mía; el Tutor sigue `activoParaMatching` (riesgo aceptado en Spec_M3) |
| E2E-KS-03 | US-7 "No" | Estudiante adulto `40000001` con Tutor `30111222` | `forzarDeteccion` en el Estudiante; responder "No" | El video vuelve; la sesión sigue `en_curso`; sin Alerta; `GET /killswitch` con `confirmacionPendiente=false` |
| E2E-KS-04 | US-7 "Sí" | Igual | Responder "Sí" | Corte; Alerta `rama=adultos` con clip |
| E2E-KS-05 | US-7 vence | Igual | No responder; esperar el plazo de la Tabla (60 s) más un margen | El diálogo se cierra, vuelve el video y la confirmación queda `vencida` en la BD; un disparo nuevo vuelve a preguntar |
| E2E-KS-06 | US-6bis, no carga (caso borde 8) | Mía | Antes de entrar, `page.route("**/modelos/nsfw/**", r => r.abort())` | "No pudimos activar la protección…", sin request a `/token` |
| E2E-KS-07 | US-6bis, cae y no revive (caso borde 8) | Tutor y Mía | Modelo falso que tira error desde un momento y no vuelve a cargar | En Mía: video remoto oculto con "Reactivando…", después de unos 10 s sale de la sala con "sin-proteccion". El backend registra `CAIDA` y `CORTE_SIN_REACTIVAR` (log). La sesión sigue el camino de conectividad (verificar el estado con el criterio de US-5) |
| E2E-KS-08 | US-6bis, cae y revive | Igual | Falla una vez y revive en el 2º intento | Video oculto unos segundos y vuelve; la clase sigue |
| E2E-KS-09 | Entre adultos, no carga | `40000001` | Modelo bloqueado | Entra igual; el backend registra `NO_CARGA` |

## 5. Trazabilidad (commit 2)

Agregá en `docs/specs/Spec_M3_Aula_Virtual.md`, al final de §5 Casos Borde, una tabla **"Tests del
kill-switch (M3C)"** con: historia o caso borde → test de backend (M3C-02) → test de orquestador
(M3C-05) → E2E (este archivo).

## 6. Verificación

- `npx playwright test --project=e2e-real` verde en el entorno elegido. Pegá la salida, con el resumen
  `passed`, en el cuerpo del commit.
- La suite normal (`npx playwright test`) no ejecuta los `.e2e.spec.ts`.
- Backend: `./mvnw -B test` con JDK 21, con la línea `Tests run:` en el commit.
- Tildá T-M3-06f. Si cerraron también 02 a 05, **tildá T-M3-06** y cambiá el Chunk M3-C a `[x]` en
  `Tasks_Tinku_Chunks.md`, en el mismo commit.

Mensajes:
- `test(aula): E2E del kill-switch contra backend y LiveKit reales (M3C-06)`;
- `docs(m3): trazabilidad de tests del kill-switch (M3C-06)`.

## 7. NO tocar

- Los tiempos de la Tabla, para acelerar el test.
- Los tests existentes: se agregan, no se reescriben.
- `docker-compose.yml`: todo va en el override `docker-compose.e2e.yml`.
