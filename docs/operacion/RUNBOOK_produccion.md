# Runbook de producción — Tinku

> Topología y decisiones: `docs/adr/ADR-000-07.md` (enmienda T13). Este documento es el "cómo".
> **Nunca** pongas valores de secretos acá ni en el repo: solo nombres.

## 1. Topología

| Qué | Dónde | Público |
|---|---|---|
| Entrada | Cloudflare Tunnel, contenedor `cloudflared` | (servidor en casa, sin puertos abiertos) |
| Frontend (Next.js) | VPS, contenedor `frontend` | `https://tinku.site` (vía túnel) |
| Backend (Spring Boot) | VPS, contenedor `backend` | `https://api.tinku.site` (vía túnel) |
| Matching (Python) | VPS, contenedor `matching` | **No** (solo red interna) |
| Postgres 16 + pgvector | VPS, contenedor `db` (volumen `tinku-pgdata`) | **No** |
| Backup diario cifrado | VPS, contenedor `backup` → bucket en Cloudflare R2 | — |
| Imágenes | GHCR `ghcr.io/smercadocarbone/tinku-{backend,matching,frontend}` | — |
| Video | LiveKit Cloud | — |
| Pagos | MercadoPago | — |
| Email | Resend (ADR-000-06), DNS en Cloudflare | — |

Flujo: PR a `main` (CI verde) → merge → `deploy.yml` corre las 3 suites → publica imágenes con el hash
del commit y `latest` → llama al webhook de Coolify → Coolify baja las imágenes y reinicia.

## 2. Puesta en marcha (una sola vez)

### 2.1 GitHub
1. **Proteger `main`** (Settings → Branches → Add rule `main`): exigir PR, exigir que pasen
   `CI - tinku-backend`, `CI - tinku-frontend` y `CI - matching-service`, sin push directo.
2. **Environment `produccion`** (Settings → Environments → New): secretos `COOLIFY_WEBHOOK` y
   `COOLIFY_TOKEN` (se obtienen en 2.4). Hasta cargarlos, el workflow publica imágenes y avisa que no
   disparó el deploy (no falla).
3. (Opcional) Variables del repo `API_URL` y `SITE_URL` si los dominios no son `api.tinku.site` /
   `tinku.site`.
4. Mergear el PR a `main`: el primer run publica las 3 imágenes en GHCR (ver el resumen del job:
   ahí queda el tamaño de cada imagen).

### 2.2 Acceso del VPS a GHCR
Las imágenes son privadas. En el VPS (terminal de Coolify o SSH), con un token clásico de GitHub con
**solo** `read:packages`:
```bash
echo "<TOKEN>" | docker login ghcr.io -u <usuario-github> --password-stdin
```

### 2.3 Cloudflare Tunnel (en lugar de registros A: el servidor está en casa, sin IP fija)
1. Cloudflare → **Zero Trust** → **Networks → Tunnels** → **Create a tunnel** → tipo
   **Cloudflared** → nombre `tinku`.
2. En "Install connector" elegir **Docker** y copiar **solo el token** (lo que va después de
   `--token`). Ese valor es la variable `CLOUDFLARE_TUNNEL_TOKEN` en Coolify (§3). No correr el comando:
   el conector lo levanta el propio compose.
3. **Public Hostnames** del túnel:
   - `tinku.site` (subdominio vacío) → Service **HTTP** → `frontend:3000`
   - `api.tinku.site` → Service **HTTP** → `backend:8080`
4. Si existían registros `A`/`AAAA`/`CNAME` para `tinku.site` o `api` (p. ej. de Vercel), borrarlos
   antes: Cloudflare crea los CNAME del túnel solo.
5. **SSL/TLS → Edge Certificates → Always Use HTTPS: On.**
6. **No** abrir ni redirigir puertos en el router.
- Resend (email): agregar en Cloudflare los registros SPF/DKIM (y DMARC) que muestra Resend al agregar
  el dominio, y verificarlo. El remitente (`EMAIL_REMITENTE`) tiene que ser de ese dominio, p. ej.
  `Tinku <avisos@tinku.site>`.

### 2.4 Coolify
1. **Projects → New → Resource → Private Repository (with GitHub App)**, repo `usal-tinku`, rama
   `main`, **Build Pack: Docker Compose**, archivo `/deploy/docker-compose.coolify.yml`.
2. **Desactivar "Auto Deploy"** del recurso: el deploy lo dispara GitHub Actions *después* de publicar
   las imágenes (si Coolify desplegara con el push, bajaría las imágenes viejas).
3. **Dominios: ninguno.** Los pone Cloudflare Tunnel (§2.3). Si Coolify sugiere dominios
   generados (`*.sslip.io`), borrarlos: no hacen falta y abrirían otra entrada que no pasa por
   Cloudflare.
4. **Environment Variables** (tabla §3). Los `SERVICE_USER_*`/`SERVICE_PASSWORD_*` los genera Coolify:
   no los toques.
5. **Deploy** una vez a mano. Mirar logs del backend (§4).
6. **Webhook**: en el recurso → *Webhooks* copiar la "Deploy Webhook" URL → secreto `COOLIFY_WEBHOOK`.
   En *Keys & Tokens → API Tokens* crear un token con permiso de deploy → secreto `COOLIFY_TOKEN`.

### 2.5 Primer admin
El seed de admins solo corre en `dev`. En producción: registrarse normalmente en `tinku.site` y
después, en la terminal del contenedor `db` de Coolify:
```sql
INSERT INTO admin.admins (usuario_id, rol)
SELECT id, 'MODERACION_SEGURIDAD' FROM identidad.usuarios WHERE dni = '<DNI>';
-- o 'SOPORTE_FINANCIERO'. Un usuario tiene un solo rol de admin.
```
El admin tiene que volver a iniciar sesión.

## 3. Variables de entorno (Coolify → recurso → Environment Variables)

| Nombre | Obligatoria | Qué es / de dónde sale |
|---|---|---|
| `SERVICE_USER_POSTGRES`, `SERVICE_PASSWORD_POSTGRES` | auto | Usuario y clave de Postgres (Coolify) |
| `SERVICE_PASSWORD_64_JWT` | auto | Secreto del JWT (Coolify). Cambiarlo cierra todas las sesiones |
| `SERVICE_PASSWORD_64_MATCHING` | auto | Token compartido backend ↔ matching (AUD-015) |
| `CLOUDFLARE_TUNNEL_TOKEN` | **sí** | Token del conector del túnel (§2.3) |
| `APP_URL_PUBLICA` | sí (default `https://tinku.site`) | Enlaces de los emails y CORS |
| `BACKUP_PASSPHRASE` | **sí** | Clave de cifrado de los backups. Guardarla en el gestor de contraseñas: sin ella no se restaura |
| `BACKUP_S3_ENDPOINT` | **sí** | `https://<account-id>.r2.cloudflarestorage.com` |
| `BACKUP_S3_BUCKET`, `BACKUP_S3_ACCESS_KEY_ID`, `BACKUP_S3_SECRET_ACCESS_KEY` | **sí** | Bucket de R2 y un token de API de R2 con permiso solo sobre ese bucket |
| `RESEND_API_KEY`, `EMAIL_REMITENTE` | para email | Resend. Vacías = avisos solo en la app; el reset de contraseña no llega |
| `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | para el aula | LiveKit Cloud |
| `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` | para cobrar | MercadoPago. Sin el secret el webhook rechaza todo |
| `LLM_PROVEEDOR`, `LLM_API_KEY` | para resúmenes | Vacías = fail-closed |
| `TARIFA_PISO_HORA_ARS` | no (6140) | Piso por hora (T06); se revisa una vez por mes |
| `TINKU_TAG` | no (`latest`) | Versión de las imágenes. Para volver atrás, ver §6 |

## 4. Qué mirar en un deploy
- Backend: `Successfully applied N migrations` (o `Schema ... is up to date`) y
  `Started TinkuApplication`. Si falta una variable crítica, el arranque falla con el nombre de la
  variable (ArranqueSeguroValidator): corregir y redeployar.
- Matching: la primera búsqueda descarga el modelo (queda en el volumen `tinku-models`).

## 5. Verificación posterior
1. `curl -s -o /dev/null -w '%{http_code}\n' https://api.tinku.site/api/usuarios/me` → **403** (vivo y
   sin sesión). 5xx o timeout = caído.
2. `https://tinku.site` carga; login con un usuario real.
3. Búsqueda de tutores devuelve resultados (matching + base).
4. El Modo Bypass está bloqueado en `prod` (FASE2-07): la prueba de pago es con una reserva real de
   monto mínimo.
5. **Todos los usuarios tienen que volver a iniciar sesión** después del primer deploy con FASE3-03.

## 6. Vuelta atrás
| Tipo de deploy | Cómo volver |
|---|---|
| Sin migraciones, o solo aditivas con default | Coolify → Environment Variables → `TINKU_TAG=<hash del commit anterior>` → Redeploy. Después, volver a `latest` con el próximo arreglo |
| Con una migración no retrocompatible (p. ej. columna `NOT NULL` sin default) | Restaurar el backup previo al deploy (§7) **o** arreglar hacia adelante. **Antes de cada deploy con migraciones: backup manual** |

Los hashes están en el resumen de cada run de `Deploy - produccion` en GitHub Actions.

## 7. Backups
- Diario automático (contenedor `backup`), cifrado, 14 días en R2. Backup manual inmediato:
  `docker exec <contenedor-backup> sh backup.sh`.
- Restaurar el último: `docker exec <contenedor-backup> sh restore.sh` (o `sh restore.sh <timestamp>`).
  **Pisa la base actual**: hacerlo con el backend detenido.
- Ensayar la restauración en un VPS o base de prueba antes del piloto (§8).

## 8. Ensayo antes del piloto (lo completa la persona que opera)
| Fecha | Qué se ensayó | Resultado |
|---|---|---|
| | Deploy de un tag por merge a `main` | |
| | Vuelta atrás con `TINKU_TAG` | |
| | Restauración de un backup en una base de prueba | |

## 9. Accesos
Credenciales en el gestor de contraseñas del equipo (nunca en el repo): Coolify, proveedor del VPS,
Cloudflare (DNS y R2), Resend, LiveKit, MercadoPago, token de GHCR del VPS, `BACKUP_PASSPHRASE`.
