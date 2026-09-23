# T02 — CAP para tutores de menores: backend (DT6, FR-ID-021 a 026)

**Branch:** `tesis/cap-menores` (sub-branch `tesis/cap-backend`) ·
**Riesgo:** **ALTO** (seguridad del menor) · **Bloqueada por:** T01 mergeada (PT1 y PT10 resueltas).

> **Cómo ejecutar esta spec.** Es una **receta**: pasos **en orden**, **un commit por paso**, con su
> **punto de control**. Si un punto de control falla, **PARAR**. Protocolo y guardrails: los de
> `docs/superpowers/specs/remediacion/00-LEEME-opencode.md` (§2, §3). Los nombres entre comillas
> invertidas fueron verificados contra el código al 2026-09-23.
> Suite (desde `backend/`):
> `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test 2>&1 | grep -E "Tests run:.*Failures|BUILD"`

## 1. Contexto (verificado)

El commit `ebf6cc0` (2026-09-10, "retirar CAP del onboarding") borró la implementación del CAP: 23
archivos, 987 líneas. **Las tablas siguen en la base** (V6: `identidad.certificados_antecedentes_penales`,
con `estado IN ('PENDIENTE','APROBADO','RECHAZADO','EN_REVISION_LEGAL','VENCIDO')`, `fecha_emision`,
`vence_at`, `tiene_antecedentes`, `categoria_antecedente`, `numero_intento 1..3`, `ciclo_espera_hasta`,
`admin_revisor_id`, `revisado_at`). La migración V21 limpió de Quartz el job huérfano `capVencimiento`.

**Decisiones resueltas (2026-09-23):**
- **PT1:** todo CAP que informe **cualquier** antecedente fuera de la lista de rechazo automático
  (BR-CAP-01) queda `EN_REVISION_LEGAL` y **no habilita** (fail-closed) hasta que la asesoría legal
  defina el criterio.
- **PT10:** si el CAP vence o se revoca y el tutor tiene reservas **futuras** con menores → esas
  reservas se cancelan con **reembolso total** y aviso al Adulto Responsable. El tutor sigue habilitado
  para adultos.

## 2. ⚠️ Trampas verificadas (leé las seis antes de empezar)

**TR1 — NO restaures archivos que todavía existen.** `ebf6cc0` también **modificó** archivos que
siguen vivos y que cambiaron mucho después (FASE 1 de la auditoría): `CredencialService`,
`TutorController`, `IdentidadExceptionHandler`, `QuartzConfig`, `CredencialServiceTest`,
`IdentidadFlujosIntegracionTest`, `MatchingFlujosIntegracionTest`, `frontend/.../cuenta/page.tsx`,
`frontend/.../registro/tutor/page.tsx`. Un `git show ebf6cc0^:<esos archivos> > <archivo>` **borra los
fixes de seguridad de AUD-007, AUD-013 y AUD-033**. De esos archivos solo se **copian a mano** los
fragmentos del CAP (ver Paso 1). Se restauran **enteros** únicamente los 11 que se borraron por
completo (lista en el Paso 1).

**TR2 — El código viejo prende y apaga `activoParaMatching`. Eso ya NO va.** En la versión anterior,
`CertificadoService.revisar` hacía `tutor.setActivoParaMatching(true)` al aprobar y `marcarVencidos`
hacía `tutor.setActivoParaMatching(false)` al vencer. Con DT6, **la credencial académica es lo único que
habilita a enseñar a adultos**; el CAP define una capacidad **aparte** (`habilitadoParaMenores`).
**Ninguna línea de esta tarea toca `activoParaMatching`.** Si la restaurás tal cual, un tutor con el CAP
vencido desaparece también para los adultos.

**TR3 — El `AdminCapController` viejo NO tenía control de rol.** Tenía un comentario "M8 proveerá la
identidad real del Admin". Restaurado tal cual, **cualquier usuario autenticado podría aprobar su
propio CAP**. Todo endpoint de revisión o descarga exige `gate.requiereModeracion(authentication)`
(`AdminModeracionGate`, igual que `admin/web/ColasModeracionController`).

**TR4 — La subida vieja no validaba el archivo.** Reusá lo de AUD-007: tamaño máximo y allowlist por
magic bytes (`identidad/model/TipoArchivoCredencial.detectar`, que acepta PDF, PNG y JPEG). El CAP
argentino es un PDF, así que alcanza.

**TR5 — La vigencia de 12 meses tiene que estar en la Tabla de Tiempos antes de usarla** (A3). La
agrega **T01**. Verificalo (`rg -n -i "vigencia del cap" docs/Tabla_Tiempos_Tinku.md`); si no está,
**PARAR**: T01 no está mergeada.

**TR6 — PT10 necesita un motivo de cancelación nuevo.** El CHECK de la base se llama
`reservas_motivo_cancelacion_check` y solo admite `voluntaria`, `timeout_pago`,
`revocacion_autorizacion`, `sancion`. Hay que reemplazarlo en una migración nueva (Paso 5).

## 3. Pasos

### Paso 0 — Preparación (sin commit)
- `docker info`; último número de migración; suite completa y **anotá N**.
- Confirmá TR5 (fila de vigencia en la Tabla) y que `docs/adr/ADR-M1-04.md` (de T01) existe.

### Paso 1 — Restaurar lo borrado, sin comportamiento nuevo todavía
Restaurá **enteros** estos 11 archivos (se borraron completos en `ebf6cc0`):
```bash
for f in \
  backend/src/main/java/com/tinku/identidad/dto/AccionRevisionCap.java \
  backend/src/main/java/com/tinku/identidad/dto/CapResponse.java \
  backend/src/main/java/com/tinku/identidad/dto/CargarCapRequest.java \
  backend/src/main/java/com/tinku/identidad/dto/RevisarCapRequest.java \
  backend/src/main/java/com/tinku/identidad/jobs/CapVencimientoJob.java \
  backend/src/main/java/com/tinku/identidad/model/CertificadoAntecedentesPenales.java \
  backend/src/main/java/com/tinku/identidad/model/EstadoCap.java \
  backend/src/main/java/com/tinku/identidad/repository/CertificadoAntecedentesPenalesRepository.java \
  backend/src/main/java/com/tinku/identidad/service/CapNoEncontradoException.java \
  backend/src/main/java/com/tinku/identidad/service/CertificadoService.java \
  backend/src/main/java/com/tinku/identidad/web/AdminCapController.java ; do
  git show "ebf6cc0^:$f" > "$f"
done
```
Los tests borrados (`CertificadoServiceTest`, `CapVencimientoJobTest`) **todavía no**: se rehacen en los
pasos siguientes, porque los viejos afirman el comportamiento de TR2.
**En este paso, ya mismo:** sacá de `CertificadoService` las dos líneas de TR2 (`setActivoParaMatching`).
Dejá los métodos compilando.
**Punto de control 1:** `./mvnw -B -q compile` compila; suite verde (N igual: todavía no hay tests
nuevos). Si no compila porque una clase vecina cambió de firma, adaptá **el archivo restaurado**, nunca
la clase vecina.
**Commit:** `feat(identidad): restaurar el modelo del CAP retirado en ebf6cc0, sin tocar el matching (ADR-M1-04)`.

### Paso 2 — Habilitación para menores (el cambio de semántica)
En `CertificadoService`:
```java
/** DT6/FR-ID-026: único lugar que decide si un tutor puede dar clases a menores.
 *  Se calcula siempre; nunca se persiste un booleano que pueda desincronizarse. */
@Transactional(readOnly = true)
public boolean habilitadoParaMenores(UUID tutorId) {
    return capRepo.existsByTutorIdAndEstadoAndVenceAtGreaterThanEqual(
            tutorId, EstadoCap.APROBADO, LocalDate.now(ReservasZonaHoraria.ZONA));
}
```
(Agregá el método derivado al repositorio. Usá la zona `ReservasZonaHoraria.ZONA` o una equivalente en
`identidad` si importar de `reservas` agrega un ciclo: verificá con
`rg -n "^import com.tinku.reservas" backend/src/main/java/com/tinku/identidad`.)
- `revisar(...)`: al **aprobar**, solo cambia el estado del CAP. Aplicá PT1: si `tiene_antecedentes` es
  `true` y la categoría **no** está en la lista de rechazo automático de BR-CAP-01 → `EN_REVISION_LEGAL`,
  nunca `APROBADO`. (La lista está en `Spec_M1`, BR-CAP-01: leela, no la inventes.)
- `cargarCap(...)`: `vence_at = fecha_emision + 12 meses` (la vigencia de la Tabla).

**Tests RED** (`CertificadoServiceTest` nuevo, unitario, con mocks del repositorio):
- `habilitado_conCapAprobadoVigente_true` · `habilitado_conCapVencido_false` · `habilitado_sinCap_false`.
- `revisar_capConAntecedenteListaRechazo_rechazado` (BR-CAP-01).
- `revisar_capConOtroAntecedente_enRevisionLegal_noHabilita` (PT1).
- `revisar_aprobar_noTocaActivoParaMatching` (TR2: verificá con `verify(usuarioRepo, never()).save(...)` o
  que el flag no cambie).
**Punto de control 2:** los tests nuevos pasan; suite verde.
**Commit:** `feat(identidad): habilitacion para menores calculada desde el CAP, separada del matching (DT6)`.

### Paso 3 — Endpoints: subida (tutor) y revisión + visor (moderación)
- **Subida** en `identidad/web/TutorController` — copiá **solo** el endpoint
  `@PostMapping(value = "/antecedentes-penales", consumes = "multipart/form-data")` de
  `git show ebf6cc0 -- backend/src/main/java/com/tinku/identidad/web/TutorController.java` (las líneas con
  `-`), y agregale **antes de guardar** las mismas validaciones que `cargarCredencial` (TR4): tamaño
  máximo y `TipoArchivoCredencial.detectar(contenido)`; si no es un tipo permitido →
  `ArchivoCredencialInvalidoException` (ya da 422).
- **Revisión** en `AdminCapController` (`/api/admin/moderacion/antecedentes-penales`, está bajo
  `/api/admin/**`, así que el `AuditoriaInterceptor` la audita solo): inyectá `AdminModeracionGate` y
  llamá `gate.requiereModeracion(authentication)` **primero** en cada método, y usá el `UUID` que
  devuelve como `adminRevisorId` (reemplaza el comentario de "M8 proveerá la identidad") (TR3).
- **Visor del documento:** `GET /api/admin/moderacion/antecedentes-penales/{id}/archivo`, **copiando el
  patrón** de `ColasModeracionController.archivoCredencial` (AUD-007): `Almacenamiento.leer`, 404 si
  `ArchivoNoDisponibleException`, `Content-Type` por magic bytes, `nosniff`, `CSP: sandbox`, `no-store`.
- **Excepciones:** `CapNoEncontradoException` → 404 en el handler que corresponda al paquete del
  controller (`AdminExceptionHandler` si lo movés a `admin`, `IdentidadExceptionHandler` si queda en
  `identidad`).

**Tests RED** (integración, en `IdentidadFlujosIntegracionTest` o uno nuevo `CapIntegracionTest`):
- `subirCap_pdfValido_201` · `subirCap_noEsPdf_422`.
- `revisarCap_usuarioComun_403` · `revisarCap_soporteFinanciero_403` · `revisarCap_moderacion_200`.
- `descargarCap_soloModeracion_403ParaOtros` y `descargarCap_moderacion_200ConBytesYAuditoria`
  (mirá cómo lo prueba `AdminPanelIntegracionTest.aud007_*`).
**Commit:** `feat(identidad): subida, revision y visor del CAP con control de rol (FR-ID-021..024)`.

### Paso 4 — Puntos de control fail-closed (FR-ID-026) · el corazón de la seguridad
En **todos** estos lugares, si hay un menor de por medio y el tutor **no** está habilitado → excepción
nueva `TutorNoHabilitadoParaMenoresException` → **409**:

| Dónde (verificado) | Condición |
|---|---|
| `identidad/service/AutorizacionService.autorizarTutor(adultoResponsable, menorId, tutorId)` | siempre (autorizar es solo para menores) |
| `reservas/service/ReservaService.crearReserva(...)` (el privado por el que pasan la reserva directa y la aprobación de solicitud) | `beneficiario.getTipo() == TipoUsuario.MENOR` |
| `reservas/service/SolicitudService.crear(menorAutenticado, request)` | siempre (solo la crea un menor) |
| `aula/SesionService.crearSalaDiferida(sesionId)` (última barrera, antes de `liveKitService.crearSala`) | beneficiario `MENOR` → **no** crea la sala, aplica PT10 a esa reserva y loguea `ERROR` |

Cómo consultarlo sin agregar imports cruzados nuevos (AUD-019): definí un **puerto** en el módulo que
consulta (ej. `reservas/port/VerificadorHabilitacionMenores`), implementado en `identidad` llamando a
`CertificadoService.habilitadoParaMenores`. Es el mismo patrón que `identidad/port/VerificadorReservasFuturas`
y `VerificadorSancionVigente`. En `identidad` (`AutorizacionService`) podés llamar al servicio directo.

**Búsqueda (M2):** `matching/MatchingContextoService` ya distingue al buscador `MENOR`
(`buscador.getTipo() == TipoUsuario.MENOR`). En ese caso, `tutoresCandidatos` excluye a los no
habilitados. **Cuando busca un Adulto Responsable, el sistema no sabe para quién es la búsqueda:** **no**
agregues un parámetro nuevo sin preguntar. Recomendación a proponerle al usuario: no filtrar la búsqueda
del AR y confiar en los puntos de control de arriba (autorizar y reservar ya son fail-closed); la
búsqueda del AR marca a los no habilitados con la insignia "Sin habilitación para menores".

**Tests RED:**
- `autorizarTutor_sinCap_409` · `autorizarTutor_conCapAprobadoVigente_201`.
- `crearReserva_beneficiarioMenor_tutorConCapVencido_409` · `aprobarSolicitud_tutorSinCap_409`.
- `crearReserva_adulto_tutorSinCap_201` (regresión: **los adultos no requieren CAP**).
- `crearSolicitud_menor_tutorSinCap_409`.
- `busquedaDeMenor_excluyeTutoresSinCap`.
- `crearSala_menorConTutorNoHabilitado_noCreaSalaYCancelaLaReserva`.
**Ojo:** los tests existentes que autorizan tutores o reservan para menores (en
`ReservasFlujosIntegracionTest`, `SesionesIntegracionTest`, `E2ERamaSeguridadIntegracionTest`,
`KillswitchIntegracionTest`, `PagosWebhookIntegracionTest`…) van a empezar a dar 409 porque sus tutores
no tienen CAP. **No los debilites:** agregá un helper de test que cargue y apruebe un CAP vigente para el
tutor del escenario (en la base, como hacen otros helpers con `...Repository.save`), y usalo en esos
escenarios. Decilo en el commit (A7).
**Commit:** `feat(seguridad): fail-closed de habilitacion para menores en autorizar, reservar y abrir la sala (FR-ID-026)`.

### Paso 5 — Vencimiento y PT10
1. **Migración** `V<n>__m4_motivo_cap_vencido.sql` (TR6):
   ```sql
   -- PT10 (T02): cancelación por vencimiento o revocación del CAP del tutor.
   ALTER TABLE reservas.reservas DROP CONSTRAINT reservas_motivo_cancelacion_check;
   ALTER TABLE reservas.reservas ADD CONSTRAINT reservas_motivo_cancelacion_check
       CHECK (motivo_cancelacion IN ('voluntaria','timeout_pago','revocacion_autorizacion','sancion','cap_vencido'));
   ```
   Y `MotivoCancelacion.CAP_VENCIDO("cap_vencido")`.
2. **`ReservaService.cancelarFuturasConMenoresPorCap(UUID tutorId)`** — mismo patrón que el
   `cancelarFuturasPorSancion(UUID)` existente (mirá cómo arma la lista y emite `reserva.cancelada`),
   pero **solo** reservas futuras (`pendiente_pago` o `confirmada`) del tutor cuyo **beneficiario es
   MENOR**, con motivo `CAP_VENCIDO`. Al emitir `ReservaCanceladaEvent` con quien canceló = **el tutor**,
   `EscrowService.onReservaCancelada` ya hace **reembolso total** (canceló alguien que no es el pagador):
   verificalo en el test, no lo reimplementes.
3. **Aviso al Adulto Responsable:** si FASE2-03 (notificaciones) está mergeada, usá el puerto
   `Notificador` con un tipo nuevo `CAP_TUTOR_VENCIDO`. Si no, log `INFO` con el id de la reserva y dejá
   un `// FIXME T02: notificar al AR cuando exista FASE2-03`.
4. **Job:** `CapVencimientoJob` (restaurado) llama a `marcarVencidos()`, que ahora: pone `VENCIDO` los CAP
   aprobados con `vence_at < hoy` y, **por cada tutor que perdió la habilitación**, llama a
   `cancelarFuturasConMenoresPorCap` (por el puerto, sin import cruzado). **No toca `activoParaMatching`**
   (TR2). Registralo en `config/QuartzConfig` copiando **solo** los dos `@Bean` del job de
   `git show ebf6cc0 -- backend/src/main/java/com/tinku/config/QuartzConfig.java` (nombre
   `capVencimiento`, grupo `m1-identidad`, cron `0 0 3 * * ?`, `durability` y `requestsRecovery` en
   `true`). La hora (03:00) es operativa, no un plazo de negocio.
**Tests RED:**
- `vencimientoCap_quitaHabilitacionMenores_mantieneMatching` (TR2).
- `vencimientoCap_conReservasConMenores_lasCancelaConReembolsoTotal` (PT10: estado `CANCELADA`, motivo
  `CAP_VENCIDO`, transacción `REEMBOLSADO`).
- `vencimientoCap_reservasConAdultos_noSeTocan`.
- `CapVencimientoJobTest` rehecho con la semántica nueva.
**Commit:** `feat(identidad): vencimiento del CAP cancela solo las clases con menores y reembolsa (PT10)`.

### Paso 6 — Documentación (commit propio)
- `REGISTRO_FINDINGS.md`: AUD-035 → `CERRADO` (las tablas de V6 vuelven a usarse).
- `Tasks_Tinku_Implementacion.md` **y** `Tasks_Tinku_Chunks.md`: T-TES-02.
- `Spec_M1`: FR-ID-021 a FR-ID-026 con lo implementado (sin borrar el texto histórico, Art. XII).
**Commit:** `docs: T02 cerrada (CAP para tutores de menores)`.

## 4. Criterios de aceptación
- 6 commits, suite verde en cada uno, N final ≥ N inicial + tests nuevos.
- `rg -n "setActivoParaMatching" backend/src/main/java/com/tinku/identidad/service/CertificadoService.java`
  → **cero** resultados (TR2).
- Con T02 y T-M3-06 cerradas, recién ahí se puede poner `tinku.menores.sesiones-habilitadas=true`
  (T10), y solo con decisión explícita del usuario.

## 5. NO tocar
- `activoParaMatching` y la regla "credencial aprobada habilita a enseñar a adultos".
- `CredencialService` y los demás archivos de TR1, salvo agregar lo estrictamente necesario.
- La firma digital del CAP (T04, opcional).
