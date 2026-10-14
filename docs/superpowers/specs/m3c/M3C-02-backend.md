# M3C-02 — Backend: `hayMenor`, puerta del token, eventos del clasificador y plazo de la rama adultos

**Tarea:** T-M3-06b · **Riesgo:** alto (Artículo II) · **Depende de:** nada · **Commits:** 2 (tests RED, implementación)

## 1. Qué falta hoy (verificado contra el código al 2026-10-14)

1. **El cliente no sabe si hay un menor.** Solo lo deduce de `pizarraHabilitada`, que es otra regla.
2. **FR-AULA-010 sin implementar.** `SesionService.obtenerToken(Usuario, UUID)` da el token aunque el
   clasificador no haya cargado.
3. **No hay dónde avisar que el clasificador falló.** Los eventos son: no cargó, se cayó, revivió y
   corte sin reactivar.
4. **La confirmación de la rama adultos no vence.** `ramaAdultos` deja una fila en
   `aula.confirmaciones_killswitch` con `respondido_id = null`. Si nadie responde, queda pendiente
   para siempre, y además `ejecutarKillswitch` ignora todo disparo posterior en esa sesión porque ve
   `confirmacionPendiente`.
5. **Un cliente que recarga la página a mitad de la pregunta** no tiene cómo saber que hay una
   pendiente.

## 2. Cambios, archivo por archivo

Todo en `backend/src/main/java/com/tinku/aula/`, salvo donde se indique otra ruta.

### 2.1 `SesionService.java`

**a) Método nuevo `hayMenor`:**
```java
/**
 * ADR-M3-07: el cliente usa esto para elegir el umbral del clasificador y para saber si
 * aplica el fail-closed de US-6bis. Ante la duda (sin sesión o sin reserva) hay menor (AGENTS §3).
 */
public boolean hayMenor(UUID sesionId) {
    return sesionRepo.findById(sesionId)
            .flatMap(s -> reservaRepo.findById(s.getReservaId()))
            .map(r -> r.getBeneficiario().getTipo() == TipoUsuario.MENOR)
            .orElse(true);
}
```
No cambies `pizarraHabilitada`: tiene su propia regla (ADR-M3-06).

**b) Puerta del token.** Cambiá la firma a
`obtenerToken(Usuario usuario, UUID sesionId, boolean clasificadorListo)`:
- Después del chequeo `esParticipante` (para que un tercero siga recibiendo 403 y no 409) y antes de
  generar el token, agregá:
  ```java
  if (!clasificadorListo && reserva.getBeneficiario().getTipo() == TipoUsuario.MENOR) {
      throw new ClasificadorNoListoException();
  }
  ```
- Actualizá el javadoc citando US-6bis, FR-AULA-010 y ADR-M3-07 §6.
- Buscá con `rg "obtenerToken\("` todos los llamadores (controller y tests) y actualizalos.

**c) Eventos del clasificador.** Método nuevo:
```java
/** ADR-M3-07 §6: solo registro. No corta nada: el corte lo produce el cliente al salir de la sala. */
public void registrarEventoClasificador(Usuario usuario, UUID sesionId, EventoClasificador evento)
```
- Carga la sesión (404 si no existe) y la reserva.
- Exige `esParticipante`; si no, `SoloParticipanteException` (403).
- Hace `log.warn("Kill-switch en el cliente: evento={} sesion={} usuario={} hayMenor={}", ...)`.
  **No persiste nada:** el Artículo V no justifica una tabla para esto.

**d) Plazo de la rama adultos:**
- Constante nueva junto a las existentes (línea ~84):
  ```java
  /** Tabla de Tiempos: "Plazo de respuesta de la rama adultos del kill-switch" (ADR-M3-07 §7). */
  private static final Duration PLAZO_CONFIRMACION_ADULTOS = Duration.ofSeconds(60);
  ```
- En `ramaAdultos`, además de lo que ya hace:
  - `confirmacion.setVenceAt(Instant.now().plus(PLAZO_CONFIRMACION_ADULTOS))`;
  - `confirmacion.setVencida(false)`;
  - después del `save`, `reprogramarVencimientoConfirmacion(sesion.getId(), confirmacion.getVenceAt())`.
- `reprogramarVencimientoConfirmacion(UUID sesionId, Instant vence)` es privado:
  - borra el trigger previo con `scheduler.unscheduleJob(triggerKey("confirmacion-ks", sesionId))`,
    ignorando una `SchedulerException`;
  - programa `VencimientoConfirmacionKillswitchJob` con `JobBuilder`/`TriggerBuilder`, grupo
    `GRUPO_JOB` y `PARAM_SESION_ID`, igual que `programarSiFalta`.
  - **No uses `programarSiFalta`:** ese método no reprograma si el trigger ya existe, y una sesión puede
    tener un segundo disparo después de un "No".
  - Agregá `if (jobClass == VencimientoConfirmacionKillswitchJob.class) return "confirmacion-ks";` en
    `prefijoDe(...)`. Así `triggerKey("confirmacion-ks", id)` da `confirmacion-ks-trigger-{id}` y la
    JobKey es `confirmacion-ks-job-{id}`, con la misma convención que el resto.
  - En el bloque que limpia todos los jobs de una sesión (el `try` con `unscheduleJob(triggerSala(...))`
    y los `deleteJob`, línea ~209), agregá el trigger y el job de `confirmacion-ks`.
- En `confirmarRamaAdultos`:
  - agregá `|| confirmacion.isVencida()` a la condición que tira `ConfirmacionNoPendienteException`
    (422);
  - después de guardar la respuesta, `scheduler.unscheduleJob(triggerKey("confirmacion-ks", sesionId))`
    ignorando la excepción.
- Método nuevo que llama el job:
  ```java
  /** ADR-M3-07 §7: sin respuesta en el plazo → se resuelve como "No". Idempotente. */
  @Transactional
  public void vencerConfirmacionRamaAdultos(UUID sesionId)
  ```
  - Si existe la confirmación, `respondidoId == null` y `!vencida`: `vencida = true`, `vio = false`,
    `respondedAt = Instant.now()`, `save` y `log.info`.
  - En cualquier otro caso no hace nada.
  - **No** crea Alerta, **no** corta y **no** emite eventos.
- En `ejecutarKillswitch`, cambiá la definición de pendiente a:
  ```java
  .map(c -> c.getRespondidoId() == null && !c.isVencida())
  ```
  Así, una confirmación vencida no bloquea un disparo nuevo (mismo criterio que el comentario existente
  sobre `vio=false`).

**e) Estado para el cliente.** Método nuevo
`EstadoKillswitch estadoKillswitch(Usuario usuario, UUID sesionId)`:
- 404 si no hay sesión; 403 si no es participante.
- Devuelve un record con:
  - `hayMenor`;
  - `confirmacionPendiente`, con la misma regla de arriba;
  - `detectadoId`, solo si hay una pendiente;
  - `venceAt`, solo si hay una pendiente.
- **Nada de la Alerta:** BR-KS-03 deja eso en M9.

### 2.2 `jobs/VencimientoConfirmacionKillswitchJob.java` (nuevo)

Copia de la estructura de `jobs/NoShowJob.java`: lee `PARAM_SESION_ID` y llama a
`sesionService.vencerConfirmacionRamaAdultos(sesionId)`.

### 2.3 `model/ConfirmacionKillswitch.java`

Campos nuevos con getters y setters:
- `@Column(name = "vence_at") private Instant venceAt;`
- `@Column(name = "vencida", nullable = false) private boolean vencida = false;`

### 2.4 Migración `backend/src/main/resources/db/migration/V47__m3_confirmacion_killswitch_vencimiento.sql`

```sql
-- ADR-M3-07 §7: plazo de respuesta de la rama adultos (Tabla de Tiempos). Sin respuesta = "No".
ALTER TABLE aula.confirmaciones_killswitch
    ADD COLUMN vence_at TIMESTAMPTZ,
    ADD COLUMN vencida  BOOLEAN NOT NULL DEFAULT FALSE;
```
Antes de escribirla, confirmá que `V47` sigue libre (`ls backend/src/main/resources/db/migration`).

### 2.5 Excepciones y DTOs nuevos

- `ClasificadorNoListoException extends RuntimeException`, mensaje: "Para entrar a una clase con un
  menor, la protección del aula tiene que estar activa. Probá con otro navegador o dispositivo."
- `web/EventoClasificador.java`: `enum EventoClasificador { NO_CARGA, CAIDA, REACTIVADO, CORTE_SIN_REACTIVAR }`.
- `web/EventoClasificadorRequest.java`: `record EventoClasificadorRequest(@NotNull EventoClasificador evento)`.
- `web/EstadoKillswitchResponse.java`:
  `record EstadoKillswitchResponse(boolean hayMenor, boolean confirmacionPendiente, UUID detectadoId, Instant venceAt)`
  con un `from(...)`.
- `web/TokenSesionResponse.java`: agregá `boolean hayMenor` **al final** del record y actualizá el
  javadoc.

### 2.6 `web/SesionController.java`

- `token(...)`:
  - agregá `@RequestParam(name = "clasificadorListo", defaultValue = "false") boolean clasificadorListo`;
  - pasalo al servicio;
  - sumá `sesionService.hayMenor(id)` al `new TokenSesionResponse(...)`.
- Endpoint `@GetMapping("/{id}/killswitch")` → `EstadoKillswitchResponse`.
- Endpoint `@PostMapping("/{id}/clasificador")` con `@Valid @RequestBody EventoClasificadorRequest`
  → `ResponseEntity.noContent().build()`.
- Actualizá el javadoc de la clase con los endpoints nuevos.

### 2.7 `web/AulaExceptionHandler.java`

Handler nuevo:
```java
@ExceptionHandler(ClasificadorNoListoException.class)
public ResponseEntity<Map<String, String>> clasificadorNoListo(ClasificadorNoListoException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", ex.getMessage(), "codigo", "CLASIFICADOR_NO_LISTO"));
}
```
El formato `{error, codigo}` es el mismo que usa `ReservasExceptionHandler` (R5).

### 2.8 Seguridad

Confirmá en `SecurityConfig` que `/api/sesiones/**` exige autenticación. Los endpoints nuevos heredan
esa regla; no agregues `permitAll`.

## 3. Tests (commit 1, RED)

En `backend/src/test/java/com/tinku/aula/web/KillswitchIntegracionTest.java`, o en un archivo nuevo
`KillswitchClienteIntegracionTest.java` en el mismo paquete con la misma configuración. Copiá el
`@TestPropertySource` y los helpers de armado de sesión del test existente.

| Test | Esperado |
|---|---|
| `token_conMenor_sinClasificadorListo_409` | 409, `codigo = CLASIFICADOR_NO_LISTO`, sin token |
| `token_conMenor_conClasificadorListo_200_hayMenorTrue` | 200 y `hayMenor = true` |
| `token_sinMenor_sinClasificadorListo_200_hayMenorFalse` | 200 y `hayMenor = false` |
| `token_tercero_conMenor_403` | 403, no 409 |
| `eventoClasificador_participante_204` | 204 |
| `eventoClasificador_tercero_403` | 403 |
| `eventoClasificador_eventoInvalido_400` | Body `{"evento":"OTRO"}` → 400. Si hoy devuelve 403 por el hallazgo TRANS-1, documentalo en el test con `// TRANS-1` y aceptá el código real |
| `estadoKillswitch_sinConfirmacion` | `confirmacionPendiente = false`, `detectadoId` null |
| `estadoKillswitch_conConfirmacionPendiente` | `true`, `detectadoId` del detectado, `venceAt` ≈ ahora + 60 s |
| `estadoKillswitch_tercero_403` | 403 |
| `ramaAdultos_programaVencimiento` | Tras el disparo, `scheduler.checkExists(new TriggerKey("confirmacion-ks-trigger-" + sesionId, "m3-aula"))` es true |
| `vencerConfirmacion_sinRespuesta_quedaVencidaComoNo` | Llamando a `vencerConfirmacionRamaAdultos`: `vencida = true`, `vio = false`, sesión `en_curso`, sin Alerta |
| `vencerConfirmacion_yaRespondida_noHaceNada` | Sin cambios |
| `confirmar_trasVencimiento_422` | 422 |
| `disparo_trasConfirmacionVencida_creaConfirmacionNueva` | `respondidoId` null, `vencida = false`, `venceAt` nuevo |
| `confirmar_cancelaElTriggerDeVencimiento` | Después de responder, el trigger no existe |

Regresión: todos los tests de `KillswitchIntegracionTest`, los de pizarra y los de grabación de audio
siguen verdes sin cambiar sus aserciones, salvo la firma de `obtenerToken` si algún test lo llama
directo.

Mensaje del commit 1: `test(aula): RED de hayMenor, puerta del token y plazo de la rama adultos (M3C-02)`.

## 4. Implementación (commit 2, GREEN)

Mensaje: `feat(aula): puerta del clasificador, estado del kill-switch y plazo de la rama adultos (M3C-02, ADR-M3-07)`.

## 5. Verificación

- `./mvnw -B test` con JDK 21: `Tests run:` ≥ 661 + los nuevos, 0 failures, 0 errors.
- `rg "obtenerToken\(" backend/src` no muestra llamadas con la firma vieja.
- En `docs/specs/Spec_M3_Aula_Virtual.md`:
  - en FR-AULA-010, reemplazá "**NO IMPLEMENTADO (AUD-001, 2026-09-21)** …" por "Puerta del token
    implementada en M3C-02 (commit …); comportamiento del cliente en M3C-05";
  - en US-7, agregá una línea sobre el plazo y su vencimiento, remitiendo a ADR-M3-07 §7.
- Tildá T-M3-06b en las dos listas de tareas.

## 6. NO tocar

- `pizarraHabilitada` y su regla.
- La decisión de rama (`reserva.getBeneficiario().getTipo()`).
- El dinero, los eventos y el aviso al Adulto Responsable.
- Migraciones existentes, en especial `V12__m3_killswitch.sql`.
