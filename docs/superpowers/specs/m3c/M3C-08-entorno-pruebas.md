# M3C-08 — Entorno de pruebas con bypass de pago y menores encendidos

**Branch:** `chunk/m3-c` · **Riesgo:** medio (infraestructura y presupuesto) · **Bloqueada por:** — · **Lo arma:** el dueño del producto en Coolify, con los archivos que prepara el agente

## 1. Problema

En producción no se pueden probar el aula ni los menores en sesión:

- **Bypass bloqueado.** `PasarelaService.bypassPermitido()` devuelve `false` con el perfil `prod`
  (FASE2-07). Sin bypass no hay reserva confirmada sin tarjeta real, y sin reserva confirmada no hay
  aula.
- **Menores apagados.** `tinku.menores.sesiones-habilitadas` es global: encenderlo en `tinku.site`
  abre las clases con menores reales sin kill-switch (AGENTS §3).

Las pruebas de `docs/operacion/HALLAZGOS_pruebas.md` dejaron todo eso como "no probado".

## 2. Implementación

### Agente

1. **Perfil `staging`** (`backend/src/main/resources/application-staging.yml`): mismas variables sin
   default que `application-prod.yml`, para que el arranque falle si falta alguna. Así:
   - `ArranqueSeguroValidator` corre igual que en producción (valida todo lo que no sea `dev`/`test`);
   - `PasarelaService.bypassPermitido()` da `true` porque no está `prod`;
   - no se toca FASE2-07: en producción el bypass sigue prohibido.
   - Test: `bypassPermitido_conPerfilStaging_true` y `bypassPermitido_conProdYStaging_false` (si alguien
     pone los dos, `prod` gana).
2. **`deploy/docker-compose.staging.yml`**: el mismo stack que `docker-compose.coolify.yml`, con:
   - `TINKU_PERFILES=staging,seed-demo` por default;
   - `TINKU_MENORES_SESIONES_HABILITADAS=true` **solo acá**;
   - volúmenes y nombres propios (base aparte, nunca la de producción);
   - el backup a R2 apagado (no hay datos reales que respaldar).
3. **`docs/operacion/RUNBOOK_staging.md`**:
   - Cómo levantarlo en Coolify y qué variables cargar.
   - Cómo cerrarlo al público.
   - Cómo prender el bypass: `PATCH /api/admin/financiero/pasarela { habilitada: false }` con el admin
     financiero.
   - Cómo apagarlo cuando no se usa.
4. Ningún cambio en `docker-compose.coolify.yml` ni en las variables de producción.

### Vos

5. En Coolify, un **recurso nuevo** con `deploy/docker-compose.staging.yml`, en un subdominio propio
   (p. ej. `staging.tinku.site` y `api.staging.tinku.site`).
6. **Cerrarlo al público:** Cloudflare Access (gratis hasta 50 usuarios) o autenticación básica del
   proxy de Coolify. El entorno usa la contraseña conocida del seed y tiene menores encendidos: no
   puede quedar abierto.
7. Variables:
   - `JWT_SECRET` distinto del de producción.
   - LiveKit: el mismo proyecto de LiveKit Cloud sirve (las salas llevan el id de la sesión). Si
     preferís aislarlo, otro proyecto gratis.
   - MercadoPago: no hace falta; se prueba en bypass.
   - Resend: se puede dejar vacío, y los avisos quedan en la bandeja in-app.
8. Agregar los hosts del entorno a *Allowed domains* de este entorno de Claude Code, como se hizo con
   `tinku.site`.
9. Promover los dos admins del seed con el `INSERT INTO admin.admins` del RUNBOOK §2.5.

## 3. Costo

Otro backend Java, otro Postgres y otro matching sobre el mismo VPS. Puede no entrar en la memoria del
VPS actual. Antes de levantarlo, mirá el uso de memoria en Coolify:

- si no entra, se levanta solo mientras dura una prueba y se apaga después;
- o se corre con `docker compose` en una máquina local.

En los dos casos se mantiene dentro de USD 0–100/mes.

## 4. Criterios de aceptación

- `https://api.staging.../actuator/health` en `UP` y `GET /api/admin/financiero/pasarela` con
  `bypassPermitido: true`.
- Una reserva de un menor del seed se crea (no da 409 `MENORES_PILOTO`).
- Sin la autenticación de acceso, el sitio no responde.

## 5. NO tocar

- `PasarelaService`: la regla "con `prod` nunca hay bypass" queda igual.
- Las variables y el compose de producción.
