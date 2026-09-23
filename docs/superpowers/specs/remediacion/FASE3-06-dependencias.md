# FASE3-06 — Escaneo de dependencias y actualización de Spring Boot (AUD-032)

**Branch:** `aud/fase3-p2-calidad` · **Riesgo:** bajo-medio (un bump de framework puede romper
cosas sutiles) · **Finding:** AUD-032.

> **Requiere buena conexión a internet** (Maven baja el framework nuevo y sus dependencias). Si la
> conexión es mala, hacé la Parte A y dejá la B para después.

## A. Dependabot (sin riesgo)

**Nuevo:** `.github/dependabot.yml` con tres ecosistemas, frecuencia semanal y límite de PRs
abiertos bajo (`open-pull-requests-limit: 3`) para no inundar a 1 desarrollador:
`maven` en `/backend`, `npm` en `/frontend` (Bun usa `package.json`; Dependabot actualiza
`package.json` pero **no** `bun.lock`: dejalo escrito en un comentario del archivo), `pip` en
`/matching-service` y `github-actions` en `/`. Agrupá las actualizaciones menores y de parche en un
solo PR por ecosistema (`groups`).

## B. Spring Boot 3.3.4 → última 3.x soportada

**Hoy:** `backend/pom.xml`, `spring-boot-starter-parent` **3.3.4**, que ya no recibe parches de la
comunidad.

1. Averiguá la última versión **3.x** estable (no saltes a una mayor nueva en esta tarea).
2. Cambiá solo la versión del parent. Corré la suite completa.
3. Si algo se rompe, **arreglá la incompatibilidad, no el test** (A7). Casos típicos a revisar: la
   API de `RestClient`, Hibernate (versión mayor distinta), Testcontainers, `jjwt`.
4. Revisá que las propiedades de `application*.yml` no hayan quedado deprecadas (los logs de arranque
   las listan como `WARN`).

## Criterios de aceptación

- Suite verde con la versión nueva. `REGISTRO_FINDINGS.md` AUD-032 → `CERRADO`.
- README: versión de Spring Boot actualizada en la tabla del stack.
