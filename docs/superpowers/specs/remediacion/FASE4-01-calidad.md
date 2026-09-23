# FASE4-01 — Calidad de código y operación (AUD-036, AUD-034 parcial) — opcional

**Branch:** `aud/fase4-p3-opcional` · **Riesgo:** bajo · **Precondición:** FASE 3 mergeada.

Tareas independientes, **un commit cada una**. Ninguna cambia comportamiento de negocio: el criterio
es la suite completa verde con el **mismo conteo**, salvo donde se indica un test nuevo.

| # | Tarea | Finding | Detalle |
|---|---|---|---|
| 4.1 | Un solo acceso a la base por request para el usuario autenticado | AUD-036.1 | Hoy el filtro JWT carga el usuario y después `UsuarioActual.obtener` lo vuelve a buscar. Poné el `Usuario` (o su id + lo mínimo) en el principal del `Authentication` y que `UsuarioActual` lo tome de ahí. Hacelo **después** de FASE3-03 (el principal pasa a ser el UUID). |
| 4.2 | Unificar `@Transactional` en el de Spring | AUD-036.3 | 6 servicios de `identidad` usan `jakarta.transaction.Transactional` (`PasswordResetService`, `UsuarioService`, `AutorizacionService`, `CredencialBackoffService`, `OcrBackoffService`, `CredencialService`). Pasarlos a `org.springframework.transaction.annotation.Transactional`. Ojo: el de jakarta **no** hace rollback por excepciones chequeadas y no soporta `readOnly`; verificá que ningún método dependa de eso. |
| 4.3 | Subpaquetes de capa en `matching` | AUD-036.2 | ~40 clases planas en `com.tinku.matching`. Separar en `model`, `repository`, `service`, `web`, `port`, igual que el resto de los módulos. Solo mover, sin cambiar lógica. |
| 4.4 | `@JsonIgnore` en `Usuario.getEdad()` | AUD-036.6 | Si `Usuario` se serializa en algún lado, `getEdad()` se cuela como campo. Verificá dónde (no debería serializarse la entidad: si pasa, eso es un hallazgo aparte, reportalo). |
| 4.5 | Actuator con `/health` e `/info` | AUD-034 | `spring-boot-starter-actuator` es dependencia nueva → **ADR corto** (A5). Exponer **solo** `health` e `info`, y `health` sin detalles para anónimos (`show-details: when-authorized`). Revisá `SecurityConfig`: `/actuator/health` público, el resto cerrado. El panel de Salud de M8 (`SaludInfraestructuraService`) puede seguir como está. |

Tareas del plan viejo que **ya no van acá:** 4.5 original (rama DNI de LiveKit) la resuelve
FASE3-03.

## Criterios de aceptación

- Suite verde en cada commit. `REGISTRO_FINDINGS.md`: AUD-036 anota cada sub-ítem cerrado (el 7 lo
  cerró FASE2-04); AUD-034 suma el actuator.
