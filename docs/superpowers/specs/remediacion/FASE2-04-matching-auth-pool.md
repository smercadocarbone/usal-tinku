# FASE2-04 — Autenticación y pool de conexiones en `matching-service` (AUD-015, AUD-036.7)

**Branch:** `aud/fase2-p1-integridad` · **Riesgo:** bajo · **Findings:** AUD-015, AUD-036 (sub-ítem 7).

## 1. Problema (verificado al 2026-09-22)

- `matching-service/main.py` (FastAPI) **no exige ninguna autenticación**, y `docker-compose.yml`
  lo publica en el host con `ports: - "8000:8000"`. Cualquiera en la misma red puede llamar a
  `/recompute-embeddings` o leer scores.
- `_conectar()` abre **una conexión nueva a Postgres por operación**; `/recompute-embeddings` abre
  una por perfil.
- `_cargar_embedder()` hace la carga perezosa del modelo sobre la global `_embedder` sin lock:
  dos requests concurrentes al arrancar cargan el modelo dos veces (AUD-036.7).
- El backend lo llama desde `matching/MatchingServiceClient.java` (`RestClient` con
  `tinku.matching-service.base-url`).

## 2. Decisiones (del arquitecto)

- **Token compartido** entre dos procesos propios, en un header. **Nada** de OAuth, usuarios ni
  JWT (Artículo VII).
- **No sacar el puerto**: bindearlo a `127.0.0.1:8000:8000`. Así `make backend` (backend en el host
  llamando a `localhost:8000`) sigue funcionando, y el servicio deja de estar expuesto en la red.
  El contenedor `backend` lo sigue alcanzando por la red interna (`http://matching:8000`).
- **Fail-closed:** si el token no está configurado en el servicio, **todos** los endpoints
  (salvo `/health`) responden 503. Nunca "sin token configurado = abierto".

## 3. PARAR — ADR por dependencia nueva (A5)

`psycopg_pool` es un paquete aparte (`psycopg[pool]`). Antes de agregarlo, escribí
`docs/adr/ADR-M2-02.md` (corto): qué problema resuelve, alternativa descartada (una sola conexión
global con reconexión: frágil ante cortes de la BD) y costo (ninguno de infra). Si preferís no
sumar la dependencia, la alternativa sin ADR es **una conexión por request reutilizada dentro del
request** y agrupar `/recompute-embeddings` en una sola conexión y transacción: cumple el finding
igual. Elegí una de las dos y justificála en el commit.

## 4. Archivos

- `matching-service/main.py`, `matching-service/requirements.txt` (si va el pool),
  `matching-service/test_main.py`
- `docker-compose.yml` (servicio `matching` y variables del `backend`), `.env.example`
- `backend/src/main/java/com/tinku/matching/MatchingServiceClient.java`
- `backend/src/main/resources/application.yml` (`tinku.matching-service.token: ${MATCHING_SERVICE_TOKEN:}`)
- `backend/src/main/resources/application-prod.yml` (`${MATCHING_SERVICE_TOKEN}` sin default)

## 5. Pasos

1. **Servicio:** dependencia de FastAPI (`Depends`) que compara el header `X-Matching-Token` contra
   `TINKU_MATCHING_TOKEN` con `hmac.compare_digest` (no `==`: evita timing). Sin header o distinto →
   401. `/health` queda público (lo usan compose y el panel de salud de M8).
2. **Pool / conexiones:** según lo que decidiste en §3. `/recompute-embeddings`: una sola
   transacción, o commit por lote de 100 perfiles.
3. **Lock** (`threading.Lock`) alrededor de la carga de `_embedder`, con doble chequeo.
4. **Backend:** `MatchingServiceClient` agrega el header en cada request. Si
   `tinku.matching-service.token` está vacío fuera de `dev`/`test`, sumalo a las validaciones de
   `config/ArranqueSeguroValidator.java` (mismo patrón que el JWT secret).
5. **Compose:** `ports: - "127.0.0.1:8000:8000"`; pasar `TINKU_MATCHING_TOKEN` al servicio y
   `MATCHING_SERVICE_TOKEN` al backend, ambos desde `.env`. En `.env.example`, **solo el nombre**
   de la variable, nunca un valor.

## 6. Tests obligatorios

- **Python** (`test_main.py`, con `fastapi.testclient`): sin token → 401; token incorrecto → 401;
  token correcto → 200; `/health` sin token → 200; servicio sin token configurado → 503.
  RED primero: hoy todos devuelven 200.
- **Backend:** test de `MatchingServiceClient` con un stub HTTP local (mismo patrón que
  `LiveKitServiceTest`) que verifica que el header viaja.
- Correr: `cd matching-service && uv run pytest` y la suite del backend.

## 7. Criterios de aceptación

- Tests de Python y suite del backend en verde. `REGISTRO_FINDINGS.md`: AUD-015 → `CERRADO`,
  AUD-036 anota el sub-ítem 7.
- `matching-service/README.md` y el README raíz: documentar `TINKU_MATCHING_TOKEN` /
  `MATCHING_SERVICE_TOKEN` (solo nombres).
- Manual: `curl -s -o /dev/null -w '%{http_code}' http://localhost:8000/health` → 200 y cualquier
  otro endpoint sin token → 401.

## 8. NO tocar

- La lógica de ranking ni el modelo de embeddings (ADR-M2-01).
