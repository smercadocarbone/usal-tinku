# M3C-08 — Entorno de pruebas (staging) con bypass de pago y menores encendidos

**Tarea:** T-M3-06h · **Riesgo:** medio (infraestructura y presupuesto) · **Depende de:** nada · **Commits:** 2 (perfil `staging` con tests; compose y runbook) · **El recurso en Coolify lo crea el dueño**

## 1. Por qué

En producción no se pueden probar el aula ni los menores en sesión:

- `PasarelaService.bypassPermitido()` devuelve `false` con el perfil `prod` (FASE2-07). Sin bypass no
  hay reserva confirmada sin tarjeta real, y sin reserva confirmada no hay aula.
- `tinku.menores.sesiones-habilitadas` es global: encenderlo en `tinku.site` abriría clases con menores
  reales (AGENTS §3).

El E2E de M3C-06 corre en local con `docker compose`. Staging sirve para que el dueño pruebe a mano en
un despliegue igual al real (build de producción, HTTPS, LiveKit Cloud), y para repetir las pruebas
exploratorias de `docs/operacion/PLAN_pruebas_produccion.md` en el aula.

## 2. Commit 1 — perfil `staging`

1. `backend/src/main/resources/application-staging.yml`:
   - **copia exacta** de `application-prod.yml`, con las mismas variables sin default;
   - comentario arriba: "Igual que prod, salvo que no activa el perfil `prod`: permite el modo Bypass
     de la pasarela (PasarelaService.bypassPermitido). Nunca en el despliegue real. M3C-08."
2. **No cambies `PasarelaService`.** Su regla ("con `prod`, nunca bypass") ya da `true` con `staging`
   y `false` si alguien pone `prod,staging`.
3. Tests nuevos en `backend/src/test/java/com/tinku/pagos/service/PasarelaServiceBypassTest.java`,
   siguiendo cómo ese test arma el `Environment`:
   - `bypassPermitido_conPerfilStaging_true`;
   - `bypassPermitido_conProdYStaging_false`.
4. `ArranqueSeguroValidator` corre igual con `staging`, porque valida todo lo que no sea `dev`/`test`.
   Agregá un test si no existe uno equivalente: `arranque_conStagingYOcrStub_falla`. Así queda
   probado que `staging` no habilita el OCR falso.
5. Mensaje: `feat(deploy): perfil staging, igual a prod pero con bypass permitido (M3C-08)`.

## 3. Commit 2 — compose y runbook

1. `deploy/docker-compose.staging.yml`: copia de `deploy/docker-compose.coolify.yml`, con estos
   cambios y ninguno más:
   - `SPRING_PROFILES_ACTIVE: ${TINKU_PERFILES:-staging,seed-demo}`;
   - `TINKU_MENORES_SESIONES_HABILITADAS: "true"`;
   - **sin el servicio `backup`**: no hay datos reales que respaldar;
   - nombres de volúmenes con sufijo `-staging`, para que nunca compartan datos con producción;
   - comentario arriba: "Entorno de pruebas. Contraseña conocida del seed y menores encendidos:
     **tiene que estar cerrado al público** (ver RUNBOOK_staging §2)."
2. `docs/operacion/RUNBOOK_staging.md`, con estas secciones:
   1. **Crear el recurso en Coolify:** recurso nuevo, este compose, subdominios `staging.tinku.site` y
      `api.staging.tinku.site`.
   2. **Cerrarlo al público:** Cloudflare Access (gratis hasta 50 usuarios), con una política de email
      del dueño. Alternativa: autenticación básica de Traefik en Coolify. Comprobación: sin sesión de
      Access, `curl -I https://staging.tinku.site` no devuelve 200.
   3. **Variables:**
      - `JWT_SECRET`, distinto del de producción;
      - `SPRING_DATASOURCE_URL` y credenciales de su propio Postgres;
      - `MATCHING_SERVICE_TOKEN`;
      - `APP_URL_PUBLICA=https://staging.tinku.site`;
      - LiveKit: el mismo proyecto de LiveKit Cloud sirve, porque las salas llevan el id de la sesión;
      - MercadoPago y Resend: vacíos.
   4. **Admins:** el `INSERT INTO admin.admins` del RUNBOOK de producción §2.5, con dos DNIs del seed.
   5. **Prender el bypass:** `PATCH /api/admin/financiero/pasarela {"habilitada": false}` con el admin
      financiero, y comprobar `bypassPermitido: true`.
   6. **Memoria del VPS:** otro backend, otro Postgres y otro matching. Antes de levantarlo, mirar el
      uso en Coolify. Si no entra, levantarlo solo mientras dure una prueba y apagarlo después. Costo:
      USD 0 adicional en el mismo VPS.
   7. **Apagar:** detener el recurso cuando no se usa.
   8. **Allowed domains:** agregar `staging.tinku.site` y `api.staging.tinku.site` al entorno de Claude
      Code, como se hizo con `tinku.site`, para que el agente pueda probar.
3. Mensaje: `docs(deploy): compose y runbook del entorno de pruebas (M3C-08)`.

## 4. Lo que hace el dueño

Los pasos 1 a 5 y 8 de `RUNBOOK_staging.md`. Cuando el entorno responde, el agente verifica:
- `GET https://api.staging.tinku.site/actuator/health` → `UP`;
- `GET /api/admin/financiero/pasarela` con el admin financiero → `bypassPermitido: true`;
- una reserva del AR `40000012` para Mía `50000001` con el Tutor `31000028` **no** da 409
  `MENORES_PILOTO`.

## 5. Verificación

- `./mvnw -B test` con JDK 21 verde, con la línea `Tests run:` en el commit.
- `git diff main -- deploy/docker-compose.coolify.yml backend/src/main/resources/application-prod.yml`
  vacío: producción no se toca.
- Tildá T-M3-06h cuando el entorno esté levantado y verificado. Hasta entonces, dejala con una nota
  "archivos listos, falta el recurso en Coolify".

## 6. NO tocar

- `PasarelaService`.
- `docker-compose.coolify.yml` y `application-prod.yml`.
- Las variables de producción.
