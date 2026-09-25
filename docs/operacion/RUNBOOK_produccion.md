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
| `BACKUP_PASSPHRASE` | para backups (**pendiente hasta configurar R2**) | Clave de cifrado de los backups. Guardarla en el gestor de contraseñas: sin ella no se restaura |
| `BACKUP_S3_ENDPOINT` | para backups (**pendiente hasta configurar R2**) | `https://<account-id>.r2.cloudflarestorage.com` |
| `BACKUP_S3_BUCKET`, `BACKUP_S3_ACCESS_KEY_ID`, `BACKUP_S3_SECRET_ACCESS_KEY` | para backups (**pendiente hasta configurar R2**) | Bucket de R2 y un token de API de R2 con permiso solo sobre ese bucket |
| `RESEND_API_KEY`, `EMAIL_REMITENTE` | para email | Resend. Vacías = avisos solo en la app; el reset de contraseña no llega |
| `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | para el aula | LiveKit Cloud |
| `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` | para cobrar | MercadoPago. Sin el secret el webhook rechaza todo |
| `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_OAUTH_REDIRECT_URI`, `TINKU_CLAVE_CIFRADO` | para el reparto con los Tutores (ADR-M5-02) | App de marketplace de MP. Redirect: `https://api.tinku.site/api/pagos/mp/callback`. Clave: `openssl rand -base64 32` (si se pierde, cada Tutor tiene que reconectar). Sin `MP_CLIENT_ID` se cobra todo en la cuenta de Tinku, sin reparto |
| `LLM_PROVEEDOR`, `LLM_API_KEY` | para resúmenes | `LLM_PROVEEDOR=gpt-4o` + API key de OpenAI (ADR-M6-03). Vacías = fail-closed |
| `TINKU_RESUMEN_ADICIONAL_HABILITADO` | no (`false`) | Adicional pago de resumen (T09). **Dejarlo en `false` hasta tener el texto legal de la cláusula de grabación** (ADR-M3-04). Con `true` exige `LLM_PROVEEDOR=gpt-4o` y `LLM_API_KEY` o el backend no arranca |
| `TINKU_RESUMEN_ADICIONAL_PRECIO_ARS` | no (770) | Precio del adicional (PT5) |
| `TINKU_CLAUSULA_GRABACION_VERSION` | no (`borrador-1`) | Versión vigente de la cláusula. Al cambiar el texto, subila: todos vuelven a aceptar |
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
> **Mientras no estén cargadas las variables `BACKUP_*`, no hay backup fuera del servidor**: el
> contenedor `backup` falla cada noche con error en su log. Configurarlas antes de tener usuarios reales.
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

## 10. Datos demo (probar antes de tener usuarios reales)
Registrarse en producción exige la foto de un DNI real (OCR), así que para probar se cargan cuentas
demo directo en la base: `deploy/demo/cargar-demo.sql`. Todas usan DNIs `99900xxx` y la misma
contraseña, que elegís vos al cargarlas (no está en el repo).

| DNI | Quién | Para probar |
|---|---|---|
| 99900101 – 99900104 | Lucía, Martín, Valentina, Nicolás (estudiantes adultos) | Buscar, reservar, pagar, entrar al aula, calificar, ver resúmenes. Lucía ya tiene historial |
| 99900001 – 99900008 | María (Matemática), Juan (Inglés), Ana (Física/Química), Carlos (Programación), Laura (Historia), Diego (Anatomía), Sofía (primaria), Paula (Análisis, nueva: sin calificaciones) | Panel del tutor, franjas, tarifa, aula |
| 99900201 | Roberto (Adulto Responsable) | Menores a cargo, autorizaciones (ya autorizó a María para Tomás) |
| 99900202 | Tomás, 14 años (menor) | Que las clases con menores sigan cerradas (T-TES-10) |
| 99900301 | Carla (admin Moderación y Seguridad) | Panel de moderación |
| 99900302 | Federico (admin Soporte Financiero) | Panel financiero |

Los tutores tienen temas, tarifa, franjas (lunes a viernes 17–21, sábados 10–13), credencial
aprobada y un historial de clases ya dictadas con calificaciones, pagos liberados (marcados
`en_bypass`: sin dinero real) y resúmenes. No hay clases futuras: se reservan desde la app.

### 10.1 Cargar
1. Coolify → recurso → contenedor **db** → Terminal:
   ```
   psql -U "$POSTGRES_USER" -d tinku
   \set clave_demo 'una-contraseña-de-10-o-más'
   \set email_demo 'tu.casilla@gmail.com'
   ```
   y pegar el contenido entero de `deploy/demo/cargar-demo.sql`. `email_demo` tiene que ser una
   casilla real: cada cuenta queda como `tu.casilla+tinku-<nombre>@gmail.com`, así los emails de
   prueba te llegan a vos.
2. Embeddings del buscador (sin esto los tutores no aparecen en la búsqueda): contenedor
   **matching** → Terminal:
   ```
   python -c "import os,urllib.request as u; r=u.Request('http://localhost:8000/recompute-embeddings', method='POST', headers={'X-Matching-Token': os.environ['TINKU_MATCHING_TOKEN']}); print(u.urlopen(r, timeout=600).read().decode())"
   ```
   Tiene que responder `{"actualizados": N}`. Hace falta acá porque el script carga los temas
   directo en la base; cuando un tutor cambia sus temas desde la app, el backend pide el recompute
   solo.
3. **Foto de cada tutor de la demo** (FR-ID-028, obligatoria desde 2026-09-25): sin foto un tutor
   **no aparece en la búsqueda**. El script no puede crear archivos, así que entrá con cada tutor
   de la demo → **Mi perfil → Subir foto** (cualquier JPG o PNG).

### 10.2 Qué hace falta para el recorrido completo
- **Pagar una reserva:** credenciales de **prueba** de MercadoPago (`MP_ACCESS_TOKEN` que empieza
  con `TEST-`, y `MP_WEBHOOK_SECRET` del webhook `https://api.tinku.site/api/webhooks/mercadopago`
  configurado en la app de MercadoPago). En `prod` no hay Modo Bypass (FASE2-07): sin MercadoPago la
  reserva queda en `pendiente_pago`. Se paga con un usuario comprador de prueba y tarjetas de prueba.
- **Entrar al aula:** `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` (LiveKit Cloud, plan gratis).
- **Emails:** `RESEND_API_KEY` y `EMAIL_REMITENTE`. Opcional: sin eso los avisos quedan en la app.
- **Resumen automático:** con `LLM_PROVEEDOR=gpt-4o`, `LLM_API_KEY` y `TINKU_RESUMEN_ADICIONAL_HABILITADO=true`: el tutor habilita el resumen en su perfil, el alumno lo agrega al reservar, el navegador del tutor graba solo el audio y lo sube al terminar; el resumen aparece minutos después. Si falla, se reembolsa el adicional.

### 10.3 Antes de abrir a usuarios reales
Pegar `deploy/demo/borrar-demo.sql` en el psql del contenedor **db**. Borra las cuentas demo y todo
lo que se creó con ellas probando (reservas, pagos, denuncias, notificaciones, jobs de Quartz
pendientes). Los dos admins demo no se pueden borrar: el log de auditoría es append-only (V16).
Se desactivan y se anonimizan como una baja. Después, crear el admin real (§2.5) y cambiar las
credenciales de MercadoPago de prueba por las de producción.
