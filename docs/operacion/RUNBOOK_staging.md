# RUNBOOK — despliegue de pruebas del aula (M3C-08)

Para probar a mano el aula y el kill-switch con menores y **sin cobro real**. Mientras `tinku.site`
no tenga usuarios reales, el mismo despliegue de Coolify hace de entorno de pruebas; cuando los
tenga, esto pasa a un recurso aparte (`deploy/docker-compose.staging.yml`, pendiente de M3C-08).

## 1. Variables en Coolify (recurso de `deploy/docker-compose.coolify.yml`)

| Variable | Valor para pruebas | Valor real |
|---|---|---|
| `TINKU_PERFILES` | `staging,seed-demo` (o `staging` sin cuentas sembradas) | `prod` |
| `TINKU_MENORES_SESIONES_HABILITADAS` | `true` | `false` |

- `staging` = `application-staging.yml`, copia exacta de `prod` salvo que no activa el perfil `prod`:
  por eso `PasarelaService.bypassPermitido()` da `true`. `prod,staging` sigue sin bypass.
- `seed-demo` crea cuentas con contraseña conocida: **el despliegue tiene que estar cerrado al
  público** mientras esté puesto (Cloudflare Access o autenticación básica de Traefik en Coolify).
- Redesplegar el backend después de cambiar las variables.

## 2. Prender el bypass de MercadoPago

Con un admin financiero (ver RUNBOOK de producción §2.5 para promoverlo):
`PATCH /api/admin/financiero/pasarela {"habilitada": false}` → la respuesta tiene que traer
`bypassPermitido: true`. Desde ahí, `POST /api/pagos/preferencia` confirma la reserva sin cobrar.

## 3. Comprobar

- `GET https://api.tinku.site/actuator/health` → `UP`.
- `GET /api/admin/financiero/pasarela` → `bypassPermitido: true`.
- Una reserva de un AR para un Menor **no** da 409 `MENORES_NO_HABILITADAS` (hasta 2026-10-18 se llamaba `MENORES_PILOTO`).

## 4. Límite conocido

El build de producción no trae el modelo falso del clasificador (`__tinkuModeloFalso`, solo en
desarrollo): en este despliegue no se puede forzar una detección. Se prueba la entrada protegida, el
corte por clasificador caído (bloqueando `/modelos/nsfw/**` en las DevTools) y las ramas disparando
el kill-switch a mano contra la API. Los 9 casos completos son el E2E de M3C-06 (local).

## 5. Volver a modo real

`TINKU_PERFILES=prod`, `TINKU_MENORES_SESIONES_HABILITADAS=false`, redesplegar, y
`PATCH /api/admin/financiero/pasarela {"habilitada": true}`. Encender menores de verdad es M3C-09.
