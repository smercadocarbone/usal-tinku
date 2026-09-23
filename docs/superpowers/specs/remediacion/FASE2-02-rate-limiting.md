# FASE2-02 — Rate limiting en endpoints públicos y bloqueo por intentos de login (AUD-012)

**Branch:** `aud/fase2-p1-integridad` · **Riesgo:** bajo · **Finding:** AUD-012 · **Decisión:** D8
(bucket en memoria del proceso) · **Bloqueada por:** **P4** (valores de tiempo).

## 1. Problema (verificado al 2026-09-22)

`SecurityConfig` deja con `permitAll()` siete endpoints sin ningún límite:
`/api/usuarios/registro`, `/api/usuarios/verificar-dni`, `/api/usuarios/login`,
`/api/tutores/registro`, `/api/tutores/verificar-dni`, `/api/usuarios/recuperar-password` y
`/api/usuarios/resetear-password`. Los dos webhooks (`/api/webhooks/livekit`,
`/api/webhooks/mercadopago`) están protegidos por firma pero no contra inundación.

- `/verificar-dni` es **el DoS más barato del sistema**: público, sin auth, y dispara Tesseract
  in-process (CPU-bound). Es la prioridad.
- `/login` no tiene bloqueo por intentos, y el usuario es el DNI (8 dígitos, enumerable).
- La contraseña solo exige `@Size(min = 8)` (en `RegistroAdultoRequest`, `RegistroTutorRequest`,
  `RegistroMenorRequest`, `CambiarPasswordRequest` y `ResetearPasswordRequest`).

## 2. PARAR (P4) — valores de tiempo

La Tabla de Tiempos no tiene ninguna ventana de rate limit ni duración de bloqueo, y A3 prohíbe
inventarlos. **Antes de implementar**, el usuario tiene que aprobar estos valores y agregarse como
filas en `docs/Tabla_Tiempos_Tinku.md` (en el mismo commit que la implementación):

| Concepto | Recomendación del arquitecto |
|---|---|
| Ventana de rate limit por IP, endpoints públicos | 20 requests / 1 min |
| Ventana de rate limit por IP, `/verificar-dni` (ambos) | 5 requests / 1 min |
| Ventana de rate limit por IP, webhooks | 120 requests / 1 min |
| Intentos fallidos de login antes del bloqueo (por DNI) | 5 |
| Bloqueo por intentos de login | 15 min, duplicándose en cada bloqueo consecutivo, tope 24 hs |

Si el usuario da otros valores, se usan esos. Todo valor va configurable en `application.yml`
(`tinku.rate-limit.*`), con default igual al de la Tabla.

## 3. Decisión (D8) y consecuencias

- **Implementación a mano, sin librería** (`ConcurrentHashMap` + ventana deslizante): no hay
  dependencia nueva, así que **no hace falta ADR** (A5). Si preferís una librería (Bucket4j),
  entonces **sí** hay que escribir `ADR-000-05` antes.
- Bucket en memoria del proceso: coherente con `ADR-000-04` (instancia única). Un reinicio resetea
  los contadores: modo de falla benigno y aceptado.
- **Dejar escrito** en el javadoc del filtro y en `ADR-000-04` (subsección "Actualización"): el
  día que se escale a más de una instancia, este bucket deja de servir junto con el scheduler.

## 4. Archivos

- **Nuevo:** `config/security/RateLimitFilter.java` (un `OncePerRequestFilter`), registrado en
  `SecurityConfig` **antes** de la autenticación.
- **Nuevo:** `identidad/service/LoginBackoffService.java` — mismo patrón que `OcrBackoffService`
  (clase de servicio con `chequearPuedeIntentar(dni)` / `registrarFallo(dni)` / `registrarExito(dni)`).
  **Decidí** si el contador de login es persistido (como `OcrBackoffService`, que usa
  `IntentoOcrRepository`) o en memoria (como D8). Recomendación: **en memoria**, coherente con D8
  y sin migración. Documentalo en el javadoc.
- `identidad/web/AuthController.java` / el servicio de login (`AuthService`): llamar al backoff.
- DTOs de password (los 5 de arriba): política mínima.
- `docs/Tabla_Tiempos_Tinku.md` (filas de P4).

## 5. Pasos

1. `RateLimitFilter`: clave = IP del cliente (`request.getRemoteAddr()`; **no** confíes en
   `X-Forwarded-For` salvo que haya un proxy configurado: si no, cualquiera evade el límite
   mandando el header). Superado el límite → **429** con `Retry-After` en segundos y cuerpo
   `{"error": "..."}` (mismo contrato de error que el resto de la API).
   Solo aplica a los paths de §1; el resto de la API ya exige JWT.
   Limpieza de entradas vencidas: al registrar cada request (barrido perezoso), nunca con un
   `@Scheduled` (A4 aplica al negocio, pero no sumes timers innecesarios).
2. `LoginBackoffService`: con 5 fallos para un mismo DNI → bloqueo. Durante el bloqueo, el login
   responde **429** con el mismo mensaje exista o no el DNI (no filtrar qué DNIs existen). Un
   login exitoso resetea el contador.
3. Política de contraseña: mínimo 10 caracteres, al menos una letra y un número, y **distinta del
   DNI**. Implementala como una anotación de validación propia (`@PasswordSegura`) reutilizada en
   los 5 DTOs, no copiando el regex 5 veces. La comparación contra el DNI va en el servicio (el DTO
   de reset no tiene el DNI).

## 6. Tests obligatorios (RED primero)

1. `verificarDni_superaElLimitePorIp_429ConRetryAfter`.
2. `login_cincoFallosSeguidos_bloqueaYResponde429_inclusoConPasswordCorrecta`.
3. `login_dniInexistente_bloqueadoIgual_mismoMensaje` (no enumeración).
4. `login_exitoso_reseteaElContador`.
5. `registro_passwordIgualAlDni_422` y `registro_passwordSinNumero_422`.
6. Regresión: el resto de la suite no se rompe. **Ojo:** los tests de integración hacen muchos
   logins y registros desde la misma IP (`127.0.0.1`). Si el filtro los rompe, **no subas el límite
   en producción**: en `application-test.yml` configurá límites altos para que la suite no dependa
   del filtro, y los tests del punto 1-4 lo prueban con límites bajos propios (`@TestPropertySource`).

## 7. Criterios de aceptación

- Suite verde. `REGISTRO_FINDINGS.md` AUD-012 → `CERRADO`.
- Filas de P4 agregadas a la Tabla de Tiempos.
- `Spec_M1`: nota con la política de contraseña y el bloqueo.

## 8. NO tocar

- Webhooks: su límite es solo por volumen; **la firma sigue siendo la autenticación**.
- No agregar Redis ni una tabla nueva (D8).
