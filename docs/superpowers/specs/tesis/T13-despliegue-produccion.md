# T13 — Despliegue de producción para el piloto (DT8)

> **Enmendada por `docs/adr/ADR-000-07.md` (2026-09-24):** todo corre en el VPS de 8 GB con Coolify
> (Postgres y frontend incluidos, sin Supabase ni Vercel). Parte A hecha en el branch
> `claude/lucid-lovelace-htlv4z`; la Parte B está en `docs/operacion/RUNBOOK_produccion.md`.

**Branch:** `tesis/despliegue` · **Riesgo:** **ALTO** (operación) ·
**Bloqueada por:** T05–T12 mergeadas y FASE 2 de remediación mergeada (al menos FASE2-02 rate limiting,
FASE2-04 matching-auth y FASE2-07 modo bypass).

> **Esta spec tiene dos partes y NO son del mismo ejecutor.**
> - **Parte A — la hace opencode, en el repo:** workflows, Dockerfiles, configuración, runbook.
> - **Parte B — la hace una PERSONA con las cuentas:** crear el VPS, instalar Coolify, DNS, cargar
>   secretos, ensayar. **Opencode no ejecuta nada de la Parte B**: la documenta en el runbook y se frena.
> Cada paso de la Parte A es un commit, con su punto de control.

## 1. Contexto y datos medidos (2026-09-23, stack local)

La tesis fija un **VPS Basic de DigitalOcean de 2 vCPU / 4 GB** (USD 24/mes) con Coolify, e imágenes
construidas en CI, nunca en el servidor (R-15: despliegue ensayado + vuelta atrás).

**Medido en el stack local actual (`docker images` / `docker stats`):**

| Servicio | Imagen | Memoria en uso |
|---|---|---|
| `matching-service` | **9,36 GB** | **2,33 GiB** (con el modelo cargado) |
| `backend` | 640 MB | ~600 MiB |
| `frontend` | 971 MB | (va a Vercel, no al VPS) |

Más Coolify (~0,5–1 GB) y Garage (T08), **el VPS de 4 GB no alcanza con la imagen actual.** La causa
principal es que `sentence-transformers` instala **PyTorch completo con las librerías de CUDA** (GPU),
que un VPS sin GPU nunca usa. Es lo **primero** que hay que resolver (Paso A1): decide si alcanza el
droplet de 4 GB o hace falta el de 8 GB (USD 48/mes), que **cambia la evaluación económica de la tesis**.

## 2. ⚠️ Trampas verificadas

**TD1 — La vuelta atrás NO siempre es "redeployar el tag anterior".** Las migraciones de Flyway van solo
hacia adelante. Si un deploy aplicó una migración que agrega una columna `NOT NULL` **sin default** (por
ejemplo, `duracion_minutos` de FASE2-01), la versión anterior de la app **no sabe completarla** y **todo
INSERT de reserva falla**. Para esos deploys, la vuelta atrás es **restaurar el backup de la base** tomado
justo antes, o **arreglar hacia adelante**. El runbook tiene que clasificar cada deploy (ver A5).

**TD2 — `matching-service` no se publica.** Solo red interna de Docker (FASE2-04). Si el firewall o
Coolify lo exponen, cualquiera en internet puede usarlo.

**TD3 — Los secretos nunca van al repo ni a la imagen.** Se cargan en Coolify (Parte B).
`application-prod.yml` solo tiene placeholders sin default (FASE 1), y `ArranqueSeguroValidator` aborta si
falta alguno crítico.

## 3. Parte A — opencode (repo)

### A1 — Achicar `matching-service` (PyTorch solo CPU) · **primero de todo**
- En `matching-service/Dockerfile`, instalar `torch` desde el índice de CPU **antes** del resto:
  ```dockerfile
  RUN uv pip install --system --no-cache --index-url https://download.pytorch.org/whl/cpu torch
  RUN uv pip install --system --no-cache -r requirements.txt
  ```
  (así `sentence-transformers` encuentra `torch` ya instalado y no baja la variante con CUDA).
- **Medir y registrar** en el runbook (tabla de §1) el tamaño de la imagen nueva y la memoria con el modelo
  cargado (hacé una búsqueda antes de medir: el modelo se carga en la primera, ver UX-02 B13):
  ```bash
  docker compose build matching && docker images | grep tinku-matching
  docker compose up -d matching && docker stats --no-stream | grep tinku-matching
  ```
- Correr los tests de `matching-service` (`uv run pytest -q`) y una búsqueda real de punta a punta.
- **PARAR y reportar** los números: con ellos el usuario decide 4 GB u 8 GB (§5 de la tesis).
**Commit:** `perf(matching): PyTorch solo CPU en la imagen (de 9,36 GB a <medido>)`.

### A2 — Imágenes en CI, publicadas en GitHub Container Registry
- Nuevo job `publicar` en `.github/workflows/ci-backend.yml` y en un workflow para `matching-service`
  (si FASE2-08 ya creó `ci-matching.yml`, agregalo ahí): corre **solo** en `push` a `main` y **solo** si el
  job de tests pasó (`needs:`); construye la imagen y la publica en `ghcr.io/<owner>/tinku-backend` y
  `ghcr.io/<owner>/tinku-matching` con el **hash del commit** como tag (y `latest`). Usar
  `docker/login-action` con `GITHUB_TOKEN` y `permissions: packages: write`. **No** uses secretos
  personales.
- Punto de control: el workflow corre verde en un PR de prueba hasta el job de tests, y el job `publicar`
  aparece como omitido en el PR (solo corre en `main`).
**Commit:** `ci: publicar imagenes de backend y matching en GHCR por hash de commit`.

### A3 — Memoria y perfil de producción
- `backend/Dockerfile`: `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"`, así
  la JVM respeta el límite de memoria del contenedor que se fija en Coolify (Parte B).
- `docker-compose.prod.yml` de **referencia** (no lo usa Coolify en runtime, documenta la topología):
  backend + matching (sin `ports`, TD2) + Garage, cada uno con `mem_limit` según lo medido en A1, y los
  **nombres** de todas las variables de entorno (sin valores).
- `config/ArranqueSeguroValidator`: verificá que en `prod` aborte si falta cualquiera de los secretos
  nuevos de T07/T08 (API key de Gemini, credenciales de Garage) además de los que ya cubre. Test por cada
  secreto agregado (mismo patrón que `ArranqueSeguroValidatorTest`).
**Commit:** `chore(deploy): limites de memoria, topologia de produccion de referencia y arranque seguro`.

### A4 — Healthcheck
Si FASE4-01 (Actuator) no está hecha, alcanza con usar un endpoint existente que no requiera sesión para
la verificación posterior al deploy. **No** agregues Actuator acá (es FASE4-01, con su ADR). Documentá en
el runbook qué URL se usa para verificar que el backend levantó.

### A5 — Runbook `docs/operacion/RUNBOOK_produccion.md`
Secciones obligatorias:
1. **Topología:** qué corre dónde (VPS: backend, matching, Garage; Supabase: Postgres; Vercel: frontend;
   LiveKit Cloud; MercadoPago).
2. **Variables de entorno:** tabla con **nombre**, quién la usa y de dónde sale el valor. **Nunca** el valor.
3. **Despliegue:** cómo Coolify toma el tag nuevo de GHCR; qué mirar en los logs (`Started TinkuApplication`,
   `Successfully applied N migrations`).
4. **Verificación posterior:** health, login, búsqueda, reserva en Modo Bypass (solo si el entorno lo
   permite: en `prod` el bypass está bloqueado por FASE2-07; usá una reserva real de monto mínimo o un
   entorno de staging).
5. **Vuelta atrás (TD1):** tabla por tipo de deploy — "sin migraciones o solo aditivas con default" →
   redeploy del tag anterior; "con migración no retrocompatible" → restaurar el backup de Supabase tomado
   antes del deploy, o arreglo hacia adelante. **Antes de cada deploy con migraciones: backup manual.**
6. **Ensayo (Parte B, paso B6):** tabla con fecha, qué se ensayó y resultado, que completa la persona.
7. **Contactos y accesos:** dónde están las credenciales (gestor de secretos), nunca las credenciales.
**Commit:** `docs(operacion): runbook de despliegue y vuelta atras para el piloto`.

## 4. Parte B — la persona con las cuentas (opencode NO la ejecuta)

Opencode deja estos pasos como checklist en el runbook y **se frena**:

- [ ] **B1** — Con los números de A1, decidir 4 GB u 8 GB (si es 8 GB, actualizar la evaluación económica
  de la tesis).
- [ ] **B2** — Crear el Droplet en la región más cercana a Argentina disponible, con **backups de
  DigitalOcean activados** y firewall que solo expone **80/443** (TD2).
- [ ] **B3** — Instalar Coolify; conectar GHCR (lectura de paquetes); crear los servicios backend,
  matching (**sin dominio público**, TD2) y Garage (subdominio propio por HTTPS, T08), con los límites de
  memoria de A1/A3.
- [ ] **B4** — Cargar las variables de entorno de la tabla del runbook en Coolify (TD3). Perfil
  `SPRING_PROFILES_ACTIVE=prod`.
- [ ] **B5** — Dominio `tinku.site`: DNS del backend y de Garage al VPS; frontend en Vercel (plan Hobby
  mientras no haya cobro real, **Pro** apenas haya operación comercial: el Hobby es de uso no comercial).
- [ ] **B6** — **Ensayo completo una semana antes del piloto**: deploy de un tag, verificación posterior,
  vuelta atrás al tag anterior, y restauración de un backup de Supabase en un proyecto de prueba.
  Registrar fecha y resultado en el runbook.

## 5. Criterios de aceptación
- Parte A: 5 commits; imagen de `matching` medida y reportada; workflows verdes; runbook completo.
- Parte B: ensayo de vuelta atrás registrado en el runbook **antes** del piloto.
- Disponibilidad y latencia (RNF-01 y RNF-04 de la tesis) medibles desde el día 1: panel de Coolify y
  paneles de LiveKit.

## 6. NO tocar
- La arquitectura: nada de consolidar Supabase o Vercel dentro del VPS (evaluado y descartado en la
  tesis: con backups, el ahorro era de USD 6,60/mes y concentraba todo en una máquina).
