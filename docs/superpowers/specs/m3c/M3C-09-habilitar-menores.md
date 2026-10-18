# M3C-09 — Precondiciones y encendido de las clases con menores en producción

**Tarea:** T-M3-06i · **Branch:** `chunk/m3-c` (documentación) · **Riesgo:** **el más alto del proyecto** (Artículo II) · **Depende de:** M3C-02 a M3C-08 y M3C-10 · **Decide:** el dueño del producto

> **Actualización 2026-10-18 (decisión del dueño).** El piloto con usuarios reales que arrancaba el
> 27/10/2026 **no se hace**, y las clases con menores se habilitan **sin piloto**. `tinku.site` sigue
> siendo una **demo pública de pruebas** (bypass de pago, perfil `seed-demo` con contraseñas
> conocidas). Esta lista ya no se mide contra una fecha de piloto: es lo que tiene que ser verdad
> antes de que el dueño encienda la flag. El estado de cada punto está abajo, con evidencia; lo que
> falta lo hace el dueño (§2.C y §3). Ver también el riesgo aceptado de §6.

## 1. Problema

`tinku.menores.sesiones-habilitadas` (`TINKU_MENORES_SESIONES_HABILITADAS`) es la única llave de las
clases con menores (AGENTS §3, T-TES-10). Con `false` (default), toda Reserva con beneficiario menor y
toda Solicitud de un menor responden 409 `MENORES_NO_HABILITADAS` ("Las clases para menores todavía no
están habilitadas."). Esta spec junta todo lo que tiene que ser verdad antes de encenderla, para que
la decisión no dependa de la memoria de nadie.

**El agente nunca enciende la flag** ni la pone en `true` en un archivo de configuración persistente
(`application*.yml`, `deploy/docker-compose.coolify.yml`). La enciende el dueño, en Coolify, después
de revisar esta lista. La única excepción es `docker-compose.e2e.yml` (E2E local, M3C-06).

## 2. Lista de precondiciones

### A. T-M3-06 cerrada — **completa**

- [x] M3C-01: ADR-M3-07 aceptado y tiempos en la Tabla (2026-10-14).
- [x] M3C-02 a M3C-05 mergeadas a `main` (PR #85, merge `03915af`): M3C-02 `9d17cb5` (RED) →
      `6598f17`; M3C-03 `5c64825` (RED) → `4e0092c`; M3C-04 `7d2eaab` (RED) → `47e0af2`; M3C-05
      `cc68182` (RED) → `0b444f2`, `d553009`.
- [x] M3C-06: E2E del kill-switch contra backend y LiveKit reales, **9 passed** (JUnit 9/0/0,
      2026-10-16, `b176df9`; trazabilidad en Spec_M3 `de0f939`). Destapó dos bugs del webhook de
      LiveKit, cerrados con RED→GREEN (`82062e3`→`d303eee`, `52b98ba`→`1fd0dab`). Última suite
      completa del backend registrada con JDK 21: **738 tests, 0 failures, 0 errors, 5 skipped**
      (`fix/rem-h`, 2026-10-15, `PLAN_remediacion_hallazgos.md` §0), por encima del baseline de 661.
- [x] M3C-07: latencia medida con el código real (`8e1a6e9`, anexo de ADR-M3-01 `7d93d0d`): wasm a
      4× p95 609 ms → detección estimada **1,8 s ≤ 3 s**. Queda opcional la fila de un celular real
      (O-10 del plan de remediación); no bloquea.
- [x] `Spec_M3`: FR-AULA-003, FR-AULA-004, FR-AULA-010 y US-6 sin marcas de "NO IMPLEMENTADO"
      (`rg "NO IMPLEMENTADO" docs/specs/Spec_M3*` vacío al 2026-10-18).
- [x] T-M3-06 tildada en `Tasks_Tinku_Implementacion.md` y el Chunk M3-C en `[x]` en
      `Tasks_Tinku_Chunks.md`, en el mismo commit (`abde53d`).
- [ ] **Las dos filas "propuesto 2026-10-14" de la Tabla de Tiempos** (carga máxima del clasificador
      de 20 s y 10 s sin respuesta) confirmadas por el dueño, quitando la marca. **Lo hace el dueño**
      (o le dice a un agente que quite la marca). El código ya usa esos valores.

### B. Hallazgos de las pruebas en producción que tocan a menores — **completa**

Cerrados en **M3C-10** (chunk REM-A, `fix/rem-a`, 2026-10-15): RED `0e25e93` → GREEN `974beca`, más
`81c022e`.

- [x] **T3-01 (alta):** un menor ya no cambia sus capacidades (`CapacidadesMenorIntegracionTest`,
      403). La restauración de Benjamín `50000002` en la base de la demo es O-6 del plan de remediación.
- [x] **T4-H1 (alta):** la cola de CAP ya no da 403 con un CAP pendiente (`AdminCapColaIntegracionTest`).
- [x] **T3-03 (media):** el menor no abre pantallas de otros roles (`menor-rutas.spec.ts`, 5 tests).
- [x] **TRANS-1 (media):** los errores de validación ya no se devuelven como 403
      (`ErroresHttpIntegracionTest`).

### C. Operación — **pendiente, todo del dueño**

- [ ] **FR-SEC-013:** plazo de retención extendida del clip en un caso escalado por contenido ilegal
      (`Spec_M9`, decía "a definir con asesoría legal antes del piloto"; hoy: antes de encender esta
      flag). No está en la Tabla de Tiempos. Es una decisión legal, no técnica: **el dueño** consulta
      a asesoría legal y le pasa el número a un agente, que lo agrega a `Tabla_Tiempos_Tinku.md`
      (fila M9) y cierra la marca en `Spec_M9`.
- [ ] **Un Tutor real con CAP aprobado y vigente**, revisado por el admin de Moderación desde la cola
      `/admin/antecedentes` (no por `PATCH` ni SQL directo). **El dueño** consigue el Tutor y el admin
      lo aprueba. Los Tutores del seed (`31000028` y los demás de `seed-demo`) no cuentan.
- [ ] **El admin de Moderación entrenado:** sabe resolver una Alerta de kill-switch dentro de la
      ventana de **12 hs** (Tabla de Tiempos, "Revisión del Admin de una Alerta de kill-switch") y
      tiene las notificaciones encendidas. **El dueño** lo confirma con un ensayo (disparar una alerta
      con cuentas de prueba y resolverla desde el panel).
- [ ] **Segundo admin de Moderación** (hallazgo T4-H3, acción O-7: `INSERT INTO admin.admins`,
      RUNBOOK de producción §2.5), **o** el dueño acepta por escrito el riesgo de un solo admin
      (Spec_M8, caso borde 3) dejando la fecha en esta línea. El conflicto de interés ya está cubierto
      en código (D-5: el panel responde 403 si el admin es parte).
- [ ] **T-M3-06h (recurso de staging):** por pedido del dueño, `tinku.site` hace de entorno de pruebas
      mientras no tenga usuarios reales (`RUNBOOK_staging.md`). No bloquea encender, pero con la flag en
      `true` en `tinku.site` las clases con menores quedan abiertas **también para las cuentas demo**
      (riesgo aceptado, §6).

## 3. Encendido (lo hacés vos, en Coolify)

Antes de empezar: §2 completa (o cada punto pendiente aceptado por escrito con fecha), y el commit
desplegado contiene `GET /api/reservas/clases-con-menores` (rama `feat/menores-sin-piloto`).

1. Backup manual inmediato (RUNBOOK de producción §7): `docker exec <contenedor-backup> sh backup.sh`.
2. Coolify → recurso de producción (`deploy/docker-compose.coolify.yml`) → *Environment Variables* →
   `TINKU_MENORES_SESIONES_HABILITADAS` = `true` → *Save* → **Redeploy** del backend. Es una variable
   de entorno: **no** se cambia el default de `application.yml` ni el compose.
3. Esperar `Started TinkuApplication` en el log del backend (RUNBOOK §4).
4. Verificar:
   - `curl -s https://api.tinku.site/api/reservas/clases-con-menores` → `{"habilitadas":true}`.
   - Con una cuenta de **prueba** de un menor (nunca un menor real): `/reservar?tutor=<id>` ya no
     muestra "Las clases para menores todavía no están habilitadas."
   - Con un AR de prueba: una reserva para su menor con un Tutor con CAP aprobado **no** da 409
     `MENORES_NO_HABILITADAS`; una con un Tutor sin CAP sigue dando 409 `TUTOR_SIN_CAP`.
5. Dejar registro: fecha y quién encendió, en T-TES-10 y T-M3-06i de `Tasks_Tinku_Implementacion.md`
   **y** en `Tasks_Tinku_Chunks.md` (se actualizan juntos, AGENTS §8).

## 4. Vuelta atrás (apagar)

1. Coolify → *Environment Variables* → `TINKU_MENORES_SESIONES_HABILITADAS` = `false` (o borrarla:
   el default es `false`) → *Save* → **Redeploy** del backend.
2. Verificar: `curl -s https://api.tinku.site/api/reservas/clases-con-menores` →
   `{"habilitadas":false}`; una reserva de un AR para su menor da 409 `MENORES_NO_HABILITADAS`.
3. Apagar corta las **reservas y solicitudes nuevas** con menores (puntos de corte:
   `ReservaService.validarNuevaClase`, que cubre la reserva directa y la aprobación de una Solicitud,
   y `SolicitudService.crear`). **No cancela las ya creadas.** Listarlas en la terminal del contenedor
   `db`:

   ```sql
   -- Reservas con beneficiario menor que todavía no ocurrieron o están en curso.
   SELECT r.id, r.horario, r.estado, r.tutor_id, r.beneficiario_id
   FROM reservas.reservas r
   JOIN identidad.usuarios u ON u.id = r.beneficiario_id
   WHERE u.tipo = 'MENOR'
     AND r.estado IN ('pendiente_pago', 'confirmada', 'en_curso')
   ORDER BY r.horario;

   -- Solicitudes de menores pendientes (con la flag apagada, aprobarlas da 409 y vencen solas).
   SELECT s.id, s.menor_id, s.tutor_id, s.horario_propuesto, s.expira_at
   FROM reservas.solicitudes_sesion s
   WHERE s.estado = 'pendiente'
   ORDER BY s.horario_propuesto;
   ```

4. Si la vuelta atrás es por un problema del kill-switch, cancelar a mano esas reservas con reembolso
   desde Soporte Financiero, y avisar a cada Adulto Responsable.
5. Registrar la fecha y el motivo en T-TES-10 y T-M3-06i (las dos listas de Tasks).

## 5. NO tocar

- El default de la property en `application.yml`: sigue `false`. Se enciende solo por variable de
  entorno en Coolify.
- El default de `deploy/docker-compose.coolify.yml` (`${TINKU_MENORES_SESIONES_HABILITADAS:-false}`).

## 6. Riesgo aceptado por el dueño (2026-10-18)

`tinku.site` es una demo pública de pruebas: el bypass de pago y el perfil `seed-demo` (60 cuentas con
la contraseña `password123`, entre ellas menores y Adultos Responsables) siguen activos (Ola 0, O-1,
O-3 y O-4 del plan de remediación, sin ejecutar). El dueño eligió **no bloquear** la combinación
"menores encendidos + cuentas demo con contraseña pública" ("aunque la web esté pública es solo para
pruebas"). Consecuencia: con la flag en `true`, cualquiera que entre con una cuenta demo de un AR puede
reservar una clase con una cuenta demo de menor sin pagar. No hay código que lo impida, a propósito.

Esto deja de ser aceptable apenas `tinku.site` tenga un usuario real: antes de eso, la Ola 0 completa
(O-1 cerrar el bypass, O-3 claves de admin, O-4 sacar `seed-demo`, RUNBOOK §10.3 borrar la demo) pasa
a ser requisito, con la flag como esté.
