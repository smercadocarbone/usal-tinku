# DISEÑO — Soluciones a la revisión por rol (menor, Adulto Responsable, estudiante adulto, Tutor, Admin)

**Fecha:** 2026-09-25 · **Estado:** diseño para decisión del dueño del producto. No hay código cambiado.
**Alcance:** los 10 problemas de la revisión por rol. Cada uno está verificado contra el código de `main`
(commit `dd90aea`) más el working directory. Las líneas se citan por símbolo y número; si alguna se corre,
ubicala con `rg` por el nombre del método (AGENTS §9).

> **Aviso (AGENTS §7): hay trabajo sin commitear en el working directory.** `docs/adr/ADR-M1-06.md` y
> `frontend/src/components/auth/TerminosClave.tsx` están *untracked*. Además hay cambios sin commitear
> en `Spec_M1`, en el spike, en `Tasks_*`, en el RUNBOOK y en `UsuarioRepository` (foto obligatoria,
> FR-ID-028), entre otros. El punto 9 se apoya en ADR-M1-06 tal como está en disco, pero **ese ADR
> todavía no existe en el historial**. Otro detalle: el diff de `Spec_M1` agrega un
> **FR-ID-030 duplicado** (ya existía FR-ID-030 para la política de contraseñas). Hay que renumerar
> uno de los dos antes de commitear.

---

## 0. Resumen

| # | Problema | ¿Se reproduce? | Gravedad | Tamaño | ADR / Spec |
|---|---|---|---|---|---|
| 1 | Tutor con capacidades de Estudiante/AR, menores a su cargo, reserva consigo mismo | **Sí, y peor**: `registrarMenor` no exige ni tipo ni capacidad, y a un Tutor suspendido se le puede reservar | **CRÍTICA** (Art. II) | M | Sin ADR (el Spec M1 ya lo prohíbe); nota en Spec M4 |
| 2 | El `marketplace_fee` no reparte nada: toda la plata cae en la cuenta de Tinku | **Sí** | **CRÍTICA** (dinero, legal) | A: L · B: M | **ADR-M5-02** (fila "Pagos" del Registro) + Spec M5 |
| 3 | El Tutor no tiene página "Mis cobros" | Sí | Alta | M | Spec M5 (US nueva) |
| 4 | El reembolso del adicional que falla solo se loguea | Sí, y la cola manual actual **no sirve** después de la liberación | Alta | M | Spec M5 (BR-PAG-11) + fila nueva en la Tabla de Tiempos |
| 5 | Si se cierra la pestaña y el webhook no llega, el pago no se confirma | Sí. **Si pagó tarde, la plata queda cobrada y nunca se devuelve** | **CRÍTICA** (dinero) | M | Filas nuevas en la Tabla de Tiempos; sin ADR |
| 6 | Selector de horarios pendiente (T-M4-13/14/15) | **Parcial**: ya está integrado y maneja el 409. Faltan tests y limpieza | Baja | S | Actualizar `Tasks_*` |
| 7 | El AR no ve junto lo de cada hijo | **Parcial**: "Mis chicos" existe. Los resúmenes con menores **no existen por diseño** | Media | S | No |
| 8 | Los Admins se crean solo por SQL | Sí. Además, un Admin puede **revisar su propia credencial** | Media (piloto) | S / M | Spec M8 (US nueva) si se hace la pantalla |
| 9 | Especialidades y verificación de credenciales | Diseño nuevo sobre ADR-M1-06. `activo_para_matching` hoy tiene **3 significados** | Alta | L | ADR-M1-06 (a commitear) + Spec M1/M2 |
| 10 | Kill-switch en el cliente (T-M3-06) | Pendiente al 100 % en el cliente | Bloquea menores | L | ADR-M3-01 ya decidido |

---

## 1. SEGURIDAD — Tutor como Estudiante/AR, menores propios y autorreserva

### 1.1 Confirmación
| Punto | Referencia | Qué pasa |
|---|---|---|
| Capacidades | `identidad/service/UsuarioService.java:225-252` (`actualizarCapacidades`) | Solo bloquea `MENOR && adultoResp` (l. 236). A un `TUTOR` le deja prender las dos capacidades |
| Alta de menor | `UsuarioService.java:129` (`registrarMenor`) + `UsuarioController.java:84-95` | **No valida ni el tipo ni la capacidad** de quien da el alta. Un TUTOR (aunque no haya prendido nada), un ADULTO sin capacidad AR y **hasta un MENOR** pueden crear un menor a su cargo. Viola FR-ID-020 y el Art. II |
| Reserva directa | `reservas/service/ReservaService.java:150-176` (`crearDirecta`) | No valida que `tutorId` sea `TUTOR`, ni que el Tutor sea distinto del pagador o del beneficiario. `SolicitudService.java:77` sí valida el tipo |
| Tutor suspendido | `ReservaService.crearReserva` (l. 426+) | No mira `estadoCuenta`. Un Tutor `SUSPENDIDA` que tenga franjas activas **sigue siendo reservable** por POST directo. M9 cancela las reservas futuras (`SancionListeners.onSancionAplicada`) pero no bloquea las nuevas |
| Mitigación accidental | `FranjaService.java:42` | Solo un TUTOR publica franjas, así que `tutorId` = ADULTO cae en `HorarioFueraDeFranjaException`. Es defensa por casualidad, no una regla |
| Frontend | `app/cuenta/page.tsx:101` | Oculta el bloque de capacidades a quien no es ADULTO. Pero el API se puede llamar directo |

**Cadena de ataque (con el flag de menores en `false` hoy):**
1. El Tutor hace `PATCH /me/capacidades {true,true}`.
2. Hace `POST /api/reservas {tutorId: él mismo}` y reserva y paga su propia clase. Con la Opción B de pagos
   (punto 2) eso permite **sacar plata de tarjetas robadas**: se paga con la tarjeta y Tinku le transfiere.
   Además puede inflar la señal de recontratación (`SenalesImplicitasService:155`, a verificar).
3. Hace `POST /api/usuarios/menores`: el menor queda a su cargo.
4. Cuando el flag pase a `true`, se autoriza a sí mismo (con CAP) y da clase a "su" menor sin ningún
   adulto independiente.

**Causa raíz:** las reglas "el Tutor es un camino de registro exclusivo" (Spec M1 §1 l. 13 y §6 l. 137)
y "solo un AR da de alta menores" (FR-ID-020) viven en el frontend y en javadocs, no en el servicio.

### 1.2 Solución
**Backend (servicios; una sola fuente de verdad por regla):**
- `UsuarioService.actualizarCapacidades`: si `tipo != ADULTO` responde **403** con
  `CapacidadesSoloAdultoException`. Cubre TUTOR y MENOR.
- `UsuarioService.registrarMenor`: exige `tipo == ADULTO && capacidadAdultoResponsable`, **antes** del
  OCR para no consumir intentos. Si no, **403** (`SoloAdultoResponsableException` de identidad).
- `ReservaService`: nuevo `exigirTutorReservable(tutor)`, que pide `tipo == TUTOR` y
  `estadoCuenta == ACTIVA`; si no, **404** `TutorNoEncontradoException`, para no filtrar el estado.
  Además `tutor.id ∉ {pagador.id, beneficiario.id}`; si no, **422** `AutoReservaNoPermitidaException`.
  Se llama desde `crearReserva`, que cubre la reserva directa, la aprobación de Solicitud y
  `validarNuevoHorario` (reprogramación).
- `SolicitudService.crear`: suma el chequeo de `ACTIVA`.
- `HorariosDisponiblesService` y `GET /franjas`: devuelven vacío si el Tutor no está `ACTIVA`.

**Migración nueva (V37 tentativa; nunca editar una aplicada):**
```sql
-- Pre-chequeo fail-closed (Art. II): un menor cuyo AR no es ADULTO frena el deploy.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM identidad.usuarios m JOIN identidad.usuarios a ON a.id = m.adulto_responsable_id
             WHERE m.tipo = 'MENOR' AND a.tipo <> 'ADULTO') THEN
    RAISE EXCEPTION 'Hay menores a cargo de un no-ADULTO: resolver a mano antes de migrar (RUNBOOK)';
  END IF; END $$;
UPDATE identidad.usuarios SET capacidad_estudiante = false, capacidad_adulto_responsable = false WHERE tipo = 'TUTOR';
ALTER TABLE identidad.usuarios ADD CONSTRAINT ck_tutor_sin_capacidades
  CHECK (tipo <> 'TUTOR' OR (NOT capacidad_estudiante AND NOT capacidad_adulto_responsable));
ALTER TABLE reservas.reservas ADD CONSTRAINT ck_reserva_tutor_ajeno
  CHECK (tutor_id <> pagador_id AND tutor_id <> beneficiario_id) NOT VALID;  -- solo filas nuevas
```
En el RUNBOOK va la consulta previa, para correrla en prod antes del deploy. Si aparecen autorreservas
históricas, se revisan a mano y después se hace `VALIDATE CONSTRAINT`.

**Frontend:** nada nuevo. El 403 y el 422 muestran el mensaje del backend.

### 1.3 Tests (cada uno tiene que fallar antes del fix)
- `IdentidadFlujosIntegracionTest`:
  - `tutorNoPuedeActivarCapacidades_403`;
  - `tutorNoPuedeRegistrarMenor_403`;
  - `menorNoPuedeRegistrarMenor_403` (Art. II);
  - `adultoSinCapacidadArNoPuedeRegistrarMenor_403`.
- `ReservasFlujosIntegracionTest`:
  - `tutorReservaConsigoMismo_422`;
  - `reservaDirectaAUnAdultoComoTutor_404`;
  - `reservaDirectaATutorSuspendido_404`;
  - `solicitudATutorSuspendido_404`.
- Test de migración: insertar un TUTOR con capacidades y verificar que el CHECK lo rechaza.

**Tamaño:** M · **ADR:** no (aplica una regla que ya está en el Spec) · **Spec:** nota en Spec M4 US-3
(tutor ≠ pagador/beneficiario; tutor ACTIVA).

### 1.4 Preguntas al dueño
- Un Tutor que además es madre o padre: con el DNI único no puede tener una segunda cuenta. ¿Se acepta
  que en el MVP **no pueda** ser AR (lo que dice el Spec) o se abre con un ADR? Recomiendo aceptarlo
  para el MVP.
- Si el pre-chequeo encuentra menores a cargo de un Tutor en prod: ¿se dan de baja o se reasignan a
  un adulto real?

---

## 2. DINERO — `marketplace_fee` con el token de la plataforma

### 2.1 Confirmación
- `pagos/port/MercadoPagoClientHttp.java:79`: la preferencia se crea con `Bearer accessToken`, que es
  **el token de Tinku**, y en l. 168-179 y 198-205 le agrega `marketplace_fee`.
- `PagoService.java:103-104` arma la comisión como `comision + adicional`.
- `LiberacionProveedorMercadoPago.java:31-37` (`liberarAlTutor`) solo hace `getPago(...)` y chequea
  `approved`. **No le paga a nadie.**
- MercadoPago solo reparte con `marketplace_fee` cuando la preferencia se crea con el access token del
  **vendedor**, obtenido por OAuth. Con el token propio, Tinku es el vendedor y cobra el 100 %.
- Los javadocs (`MercadoPagoClientHttp` l. 21-24, `LiberacionProveedorMercadoPago` l. 8-15) y la fila
  del Registro de la Constitución ("MercadoPago (Marketplace / escrow)") describen un reparto que
  **no ocurre** (AGENTS §9: el javadoc describe lo deseado, no lo real).

**Causa raíz:** falta todo el alta del vendedor (OAuth). La "liberación" se modeló como si MP la hiciera
solo.

### 2.2 Opción A — OAuth por Tutor (marketplace real)
| Pieza | Diseño |
|---|---|
| Configuración | App de MP en modo marketplace: `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_OAUTH_REDIRECT_URI`, y `TINKU_CLAVE_CIFRADO` (AES-256-GCM, 32 bytes en base64) |
| Migración | `pagos.cuentas_mp_tutor (tutor_id PK FK, mp_user_id, access_token_cifrado BYTEA, refresh_token_cifrado BYTEA, public_key, expira_at, estado CHECK IN ('CONECTADA','REVOCADA','ERROR'), conectada_at, updated_at)` y `pagos.oauth_estados (state PK, tutor_id, code_verifier, expira_at)` para el CSRF y el PKCE |
| Endpoints | `GET /api/pagos/mp/conectar` (solo TUTOR): genera `state` y el PKCE y devuelve la URL de `auth.mercadopago.com.ar/authorization`. `GET /api/pagos/mp/callback?code&state`: valida `state`, hace `POST /oauth/token` (`authorization_code`) y guarda cifrado. `DELETE /api/pagos/mp/conexion` para desconectar. `GET /api/pagos/mp/estado` |
| Cifrado | `CifradorTokens`: JDK `javax.crypto`, AES-GCM con IV aleatorio. Sin dependencias nuevas. La clave se lee de env y sin clave hay fail-closed (como hoy sin `MP_ACCESS_TOKEN`) |
| Refresh | `RefrescoTokensMpJob`, cron diario de Quartz persistido (patrón de `CapVencimientoJobRegistro`): refresca los tokens que vencen en menos de N días (`grant_type=refresh_token`). El token dura unos **180 días**. Si falla: `ERROR`, notificación al Tutor, y el Tutor deja de ser reservable |
| Preferencia | `MercadoPagoClient.crearPreferencia(request, tokenVendedor)`. `getPago`, los reembolsos y la búsqueda usan **el token del Tutor de esa Reserva**. El webhook sigue siendo de la app (misma firma) |
| Gate | El Tutor **no es reservable** sin cuenta `CONECTADA` (se suma a `exigirTutorReservable` del punto 1). Es un paso más en el checklist de UX-06 |
| Escrow de 24 h | **Deja de ser una retención real.** La plata entra a la cuenta del Tutor con los plazos de liberación de *su* cuenta. Las "24 h" pasan a ser una **ventana lógica** en la que Tinku todavía puede reembolsar con el token del Tutor. `LIBERADO` = "cerró la ventana". Si el Tutor ya retiró la plata, MP le debita el saldo (**verificar en sandbox**) |
| Reembolso total | Mismo `POST /v1/payments/{id}/refunds` con cuerpo vacío y el token del Tutor. Se devuelve también el `marketplace_fee` (**verificar**) |
| Parcial del adicional (BR-PAG-11) | **Problema:** si MP prorratea el reembolso parcial entre vendedor y marketplace, el $770 (que era de Tinku) se le descuenta en parte al Tutor. Hay que **verificarlo en sandbox**. Si prorratea, las alternativas son (i) cobrar el adicional en una segunda preferencia con el token de Tinku (dos pagos, peor UX) o (ii) devolverlo por transferencia desde la cuenta de Tinku (cola del punto 4) |
| Revocación | Si el Tutor desconecta la cuenta con escrows dentro de la ventana, **Tinku ya no puede reembolsar**. Se bloquea la desconexión con reservas confirmadas futuras o ventanas abiertas (409). Si igual lo revoca desde MP, se usa la cola manual y Tinku pone la plata |
| Impuestos | Cada Tutor cobra en su propia cuenta; Tinku factura solo su comisión. Es lo más limpio fiscalmente |

**Tamaño:** L (unas 2 semanas de un dev, más la prueba en sandbox con dos cuentas de prueba).

### 2.3 Opción B — Recaudación centralizada y pagos manuales
| Pieza | Diseño |
|---|---|
| Cobro | Como hoy: preferencia con el token de Tinku. Se **quita `marketplace_fee`**, que no tiene efecto y confunde |
| Datos de cobro | Migración `pagos.datos_cobro_tutor (tutor_id PK, cbu_cvu_alias VARCHAR(40), titular VARCHAR(120), cuit VARCHAR(11), updated_at)`. Check automático barato: los dígitos 3 a 10 del CUIT/CUIL tienen que ser el DNI verificado del Tutor. Si no coincide, 422. Sin esos datos, el Tutor no es reservable (igual que en A) |
| Liquidación | Migración `pagos.liquidaciones (id, transaccion_id UNIQUE, tutor_id, monto_neto, estado CHECK IN ('A_TRANSFERIR','TRANSFERIDA','RETENIDA'), transferida_at, referencia_operacion, admin_id)`. `LiberacionProveedor` se reemplaza por `LiberacionProveedorLiquidacion`: verifica `approved` (como hoy) y crea la línea `A_TRANSFERIR` con `neto = montoBruto − adicional − comisión`. Los reintentos de FR-PAG-007 quedan igual |
| Cola del Admin (Soporte Financiero) | `GET /api/admin/financiero/liquidaciones?estado=A_TRANSFERIR`, agrupado por Tutor con total y datos de cobro. `POST /api/admin/financiero/liquidaciones/transferidas {ids[], referencia}` (auditado por el interceptor de M8). Pestaña nueva en `/admin/pagos` |
| Cadencia | Transferencias manuales (el Admin transfiere desde la app de MP a los CVU, sin costo) con frecuencia **semanal**. Es una **fila nueva en la Tabla de Tiempos** (la decide el dueño) |
| Reembolsos | Todos siguen funcionando con el token de Tinku, incluido BR-PAG-11 **aun después de liberar** (la plata está en la cuenta de Tinku) |
| Contracargo ya liberado (FR-PAG-015) | Si la línea todavía está `A_TRANSFERIR`, pasa a `RETENIDA`. Si ya está `TRANSFERIDA`, va a la cola manual, como hoy |
| Docs | Spec M5 US-2 ("se liberan los fondos" pasa a "queda a transferir al Tutor"), ADR-M5-02, javadocs, Términos y Condiciones (Tinku cobra **por cuenta y orden** del Tutor) |
| **Impuestos (advertencia)** | Todo el bruto entra a la cuenta de Tinku. Ante ARCA y en Ingresos Brutos puede computarse como **ingreso de Tinku** si no está instrumentado como cobranza por cuenta de terceros (mandato). MP puede aplicar retenciones o percepciones sobre el total, y también se ven afectados los topes del Monotributo y la facturación del Tutor al alumno. **Hace falta un contador antes de salir con plata real.** Esto no es asesoramiento legal |

**Tamaño:** M (más o menos una semana).

### 2.4 Recomendación
Recomiendo **B para el piloto y A antes del lanzamiento abierto**, con un disparador explícito en el
ADR (por ejemplo, más de X liquidaciones por semana o el fin del piloto). Los motivos, medidos contra
1 dev y USD 0-100/mes:
1. **B es lo que el sistema ya hace de hecho.** Lo que se cambia es la contabilidad y la cola. **A**
   agrega OAuth, cifrado, refresh, un token por llamada y un cambio en la semántica del escrow.
2. **B no depende de que cada Tutor del piloto conecte bien su MP.** Es fricción de onboarding cuando
   todavía no hay Tutores.
3. **B conserva el escrow real de 24 h y BR-PAG-11 tal como están en el Spec.** En A, los dos pasan a
   ser lógicos y el parcial del adicional queda sin resolver hasta probarlo en sandbox.
4. **En contra de B:** el riesgo fiscal y el trabajo manual. En volumen de piloto son manejables. Si el
   contador dice que no, se pasa directo a A.

El ledger `pagos.liquidaciones` sirve igual en A (como registro, sin transferencias) y alimenta el
punto 3. **Lo que no puede esperar, se elija lo que se elija:** corregir los javadocs y decirle la
verdad al Tutor en la UI.

### 2.5 Tests
- **B:**
  - `liberacion_creaLiquidacionATransferir_conNetoCorrecto` (27 % sobre la sesión, adicional excluido);
  - `admin_marcaTransferidas_auditado`;
  - `tutorSinDatosDeCobro_noReservable_404`;
  - `cuitQueNoContieneElDni_422`;
  - `contracargoSobreLiquidacionPendiente_quedaRetenida`.
- **A:**
  - `callbackConStateInvalido_400`;
  - `tokenSeGuardaCifrado` (la columna no contiene el texto plano);
  - `preferenciaUsaTokenDelTutor` (stub HTTP que verifica el header);
  - `refresh_venceProntoSeRefresca`;
  - `desconectarConVentanaAbierta_409`.
- **Regresión (las dos):** `preferencia_noEnviaMarketplaceFeeConTokenPropio` (B) o
  `preferencia_seCreaConTokenDelVendedor` (A). Falla hoy.

**ADR:** **ADR-M5-02** obligatorio (cambia la fila "Pagos" del Registro). **Spec:** M5 US-2, FR-PAG-002
y FR-PAG-015; M8 US-5.

**Preguntas:** Opción A o B. Cadencia de transferencias. Contador consultado sí o no. Monto mínimo
por transferencia.

---

## 3. El Tutor no tiene "Mis cobros"

**Confirmación:** en `frontend/src/app/cuenta/*` no hay ninguna página de cobros, y el backend no tiene
ningún endpoint de transacciones para el Tutor (`PagoController` solo tiene tarifa, preferencia y
retorno). `Transaccion` no tiene `tutor_id`: se llega por `reserva_id`.

**Solución:**
- `GET /api/pagos/mis-cobros?desde&hasta` (solo TUTOR, si no 403). Hace join
  `transacciones → reservas` dentro de M5; `PagoService` ya lee `ReservaRepository`.
- Por fila devuelve: `reservaId`, fecha, nombre del alumno (solo nombre y apellido, como
  `ReservaResponse`), `precioSesion`, `comision`, `neto`, estado (`retenido` hasta `liberarAt`,
  `pausado` sin decir el motivo si es una denuncia o alerta, `liberado`/`a_transferir`, `transferido`
  con fecha y referencia en B, y `reembolsado`).
- Totales: `retenido`, `aTransferir`, `transferido`, `reembolsado`. **El adicional nunca se muestra
  como ingreso del Tutor.**
- Frontend `/cuenta/cobros`: tarjetas con los totales, una lista por mes y un enlace a la reserva. En B,
  arriba va el formulario de datos de cobro. En A, el estado de la conexión con MP.

**Tests:** `misCobros_soloTutor_403`, `misCobros_netoExcluyeAdicionalYComision`,
`misCobros_pausadoNoRevelaDenuncia`, `misCobros_noMuestraTransaccionesDeOtroTutor`. También un
Playwright `COBROS-E2E-001`.

**Tamaño:** M · **ADR:** no · **Spec:** US nueva en M5 ("Como Tutor, quiero ver lo que me deben, lo
liberado y lo reembolsado"). Cumple FR-PAG-008 en forma de historial.

**Pregunta:** ¿el Tutor tiene que ver el motivo de una pausa (denuncia o alerta)? Recomiendo que no:
mostrar solo "en revisión".

---

## 4. Reembolso parcial del adicional: sin reintento ni cola

**Confirmación:**
- `pagos/service/ReembolsoAdicionalResumenMercadoPago.java:48-55` loguea `REEMBOLSO_ADICIONAL_FALLIDO`
  y hace `return`. No persiste nada.
- La "cola de reembolsos parciales" que cita el javadoc **no lista esos casos**
  (`ColasFinancieroController` solo tiene `pagos-fallidos`, que son liberaciones).
- El endpoint manual `POST /transacciones/{id}/reembolso-parcial` (l. 116-150) exige escrow
  `retenido`/`pausado_denuncia`. Pero el fallo del resumen se detecta a menudo **a las 24 h**
  (`ResumenService.purgarAudio`, `RETENCION_MAXIMA_AUDIO`), **al mismo tiempo** que se libera
  (`EscrowService.VENTANA_LIBERACION`). Si ya se liberó, **el Admin no tiene forma de devolverlo desde
  el panel**.
- El reembolso además corre dentro de la transacción del listener de `sesion.finalizada`, con una
  llamada HTTP adentro.

**Solución (outbox con Quartz, mismo patrón que FR-PAG-007):**
- **Migración:** a `pagos.transacciones` se le suman `adicional_reembolso_estado VARCHAR(20) NULL CHECK IN
  ('PENDIENTE','FALLIDO','HECHO','RESUELTO_MANUAL')`, `adicional_reembolso_intentos INT NOT NULL DEFAULT 0`
  y `adicional_reembolso_error VARCHAR(300)`. `adicional_reembolsado_at` se queda.
- **`reembolsarAdicional`:** solo marca `PENDIENTE` y agenda `ReembolsoAdicionalJob` (`startNow`,
  persistido en la misma transacción). Guarda de idempotencia: sin `PENDIENTE`/`HECHO` previo.
- **Job:** llama a MP con `X-Idempotency-Key: adicional-{transaccionId}` (**verificar** que MP lo
  respeta en refunds) para no reembolsar dos veces si hay un timeout después de un éxito. Si sale bien
  pasa a `HECHO`. Si falla, suma un intento y reprograma con backoff 5/15/60 min. Al tercer fallo
  queda `FALLIDO`, avisa por `AlertaSoporteProveedor` y deja una notificación in-app al Admin
  financiero.
- **Guarda de estado:** se permite con la transacción en cualquier estado menos `REEMBOLSADO` total.
  En B la plata sigue en la cuenta de Tinku. En A, ver 2.2.
- **Admin:**
  - `GET /api/admin/financiero/reembolsos-adicional?estado=FALLIDO`;
  - `POST .../{transaccionId}/reintentar` (vuelve a `PENDIENTE`);
  - `POST .../{transaccionId}/resuelto-manual {nota}` (si se devolvió por fuera, desde el panel de MP).
  - Todo queda auditado. Pestaña "Reembolsos del resumen" en `/admin/pagos`.
- **Pagador:** en el detalle de la reserva, "Te devolvimos $770 del resumen" o "Estamos procesando la
  devolución".

**Tests:**
- Regresión: `adicionalFallaMp_quedaFallidoEnCola_noSoloLog`. Falla hoy.
- `backoff_5_15_60_luegoFallido`.
- `reintentoManual_exito_marcaHecho`.
- `idempotente_dobleEvento_unSoloReembolso`.
- `adicionalTrasLiberacion_seReembolsaIgual` (B).
- `bypass_soloMarca`.

**Tamaño:** M · **ADR:** no · **Spec:** BR-PAG-11 ("lo resuelve el Admin por la cola de reembolsos del
resumen", no la de FR-PAG-010) · **Tabla de Tiempos:** fila "Reintentos del reembolso del adicional:
3, backoff 5min/15min/1h".

---

## 5. Confirmación del pago sin depender de la vuelta ni del webhook

**Confirmación:**
- El pago se confirma solo por `POST /api/pagos/confirmar-retorno` (`PagoController.java:60`) o por el
  webhook (`MercadoPagoWebhookController`). El formato IPN no trae `x-signature` y se rechaza con 401
  (`MercadoPagoWebhookVerificador.esFirmaValida`).
- Tinku **no guarda el `preference_id`** (`PagoService.generarPreferencia` lo devuelve y no lo persiste).
- `ReservaTimeoutPagoJob` cancela a los 15 min sin consultar a MP.
- **Caso grave:** el usuario paga, cierra la pestaña y el webhook se pierde. La reserva vence y la
  plata queda cobrada **sin confirmar ni reembolsar**. `reembolsarPagoTardio` solo se ejecuta si llega
  alguna notificación.

**Solución:**
1. **Migración:** `pagos.preferencias_pago (reserva_id PK, preference_id, created_at, conciliado_at,
   intentos_conciliacion INT DEFAULT 0)`. Se escribe en `generarPreferencia`.
2. **Cliente MP:** `buscarPagosPorReferencia(externalReference)` →
   `GET /v1/payments/search?external_reference={reservaId}`. En la Opción A, con el token del Tutor.
3. **`ConciliacionPagosService.conciliar(reservaId)`:** por cada pago `approved`, llama a
   `EscrowService.procesarPagoAprobado(id)`. Ya es idempotente, ya valida el monto y ya reembolsa si
   llegó tarde. No hay lógica nueva de dinero.
4. **Al vencer el timeout:** `expirarPorTimeoutPago` concilia **antes** de cancelar, como mejor esfuerzo.
   Si MP no responde, cancela igual y el barrido lo resuelve.
5. **Barrido periódico:** `ConciliacionPagosJob`, cron de Quartz persistido cada N min. Revisa las
   preferencias con la reserva `pendiente_pago` y más de N min, o `cancelada/TIMEOUT_PAGO` sin
   `Transaccion` y con menos de H horas. Tope de H horas y alerta al Admin financiero si al final queda
   un pago aprobado sin atribuir.
6. **La preferencia no acepta pagos tardíos:**
   - `expires=true` y `expiration_date_to = created_at + 15 min` (el mismo plazo de la Tabla);
   - `payment_methods.excluded_payment_types = [ticket, atm]`, porque Rapipago y Pago Fácil se
     acreditan después de la ventana.
7. **Frontend `/pagar`:** si vuelve sin `payment_id` o con `pending`, muestra "Estamos confirmando tu
   pago" y consulta `GET /api/reservas/{id}` durante un rato. En `/cuenta/reservas` el estado ya se
   refleja.
8. **Webhook:** configurar en el panel de MP **solo Webhooks** (con firma), no IPN. La conciliación
   pasa a ser la red que garantiza la confirmación.

**Tests:**
- Regresión: `pagoAprobadoSinWebhookNiRetorno_seConfirmaPorConciliacion`. Falla hoy.
- `pagoAprobadoTrasTimeout_sinWebhook_seReembolsaPorBarrido`.
- `timeoutConcilia_antesDeCancelar`.
- `mpCaido_elTimeoutCancelaIgual`.
- `preferenciaLlevaExpiracionYExcluyeTicket` (stub HTTP).
- `barridoRespetaTopeH`.

**Tamaño:** M · **ADR:** no · **Tabla de Tiempos:** "Conciliación de pagos pendientes: cada N min,
durante H hs" (propuesta: 5 min y 48 h).

---

## 6. Selector de horarios (T-M4-13/14/15)

**Confirmación: casi resuelto, no se reproduce como está escrito.**
- `frontend/src/app/reservar/page.tsx` ya consume `GET /api/tutores/{id}/horarios` (l. 124) y arma
  los bloques por día y duración.
- Maneja el **409** (l. 184-191): tacha el horario, vuelve a pedir la ocupación del día, vuelve al
  paso 1 y muestra "Ese horario se acaba de ocupar".
- El menor genera la Solicitud (l. 165).
- El backend tiene tests de T-M4-12 (`ReservasFlujosIntegracionTest:778-846`) y AUD-009 (solapamiento)
  está **CERRADO**.
- Las notas "Verificado 2026-09-21: pendiente real" de `Tasks_Tinku_Implementacion.md:114-116` están
  **desactualizadas**. Lo que falta de verdad:

| Falta | Solución |
|---|---|
| El 409 se distingue por una **regex sobre el mensaje** (`!/menores/i.test(err.message)`, l. 184). Un 409 de `SolicitudDuplicadaException` se muestra como "horario ocupado" | El backend agrega `codigo` al cuerpo de error (`HORARIO_OCUPADO`, `SOLICITUD_DUPLICADA`, `MENORES_PILOTO`, `TUTOR_SIN_CAP`) en `ReservasExceptionHandler`. El frontend decide por `codigo` |
| No hay test E2E del 409 | Playwright `RESERVAR-E2E-004`: mock 409 `HORARIO_OCUPADO`, se verifica el horario tachado, la vuelta al paso 1 y el botón deshabilitado |
| `RESERVAR-E2E-002` dice "el Menor no ve el formulario" | Actualizarlo: hoy el menor ve el picker y envía un pedido |
| `components/DynamicTimeSlotPicker.tsx` es código muerto | Borrarlo, o registrar la decisión de no usarlo |
| Tareas sin tildar | Tildar T-M4-13/14 con la nota "resuelto por UX-04 con picker propio". T-M4-15 se cierra con los tests de arriba. Actualizar `Tasks_Tinku_Implementacion.md` **y** `Tasks_Tinku_Chunks.md` juntos (AGENTS §8) |

**Tamaño:** S · **ADR:** no · **Pregunta:** ¿se acepta el picker actual como cierre de T-M4-13/14
aunque no tenga la "píldora que vibra" del texto original?

---

## 7. Vista del AR por hijo

**Confirmación: parcial.**
- `/cuenta/menores` ("Mis chicos", `app/cuenta/menores/page.tsx:46-75`) ya muestra los pedidos para
  aprobar, cuántas clases próximas tiene cada hijo y la **próxima**.
- `GET /api/reservas` ya le devuelve al AR las reservas de sus hijos (él es el pagador) con
  `beneficiarioNombre`.
- **Los resúmenes con menores no existen por diseño:** Art. II y ADR-M3-04, con el chequeo en
  `AdicionalResumen.java:47,64`. No es un faltante. La UI tiene que decirlo ("En las clases de menores
  no se graba ni se genera resumen").
- Falta:
  - ver **todas** las clases de cada hijo, próximas y pasadas;
  - ver y gestionar los **Tutores autorizados por hijo**: `AutorizacionController` solo tiene `POST` y
    `PATCH /no-confiable`, no tiene listado.

**Solución:**
- `GET /api/autorizaciones?menorId=` (solo el AR de ese menor; si no, 404): Tutor, fecha y
  `noConfiable`.
- Frontend `/cuenta/menores/[id]`:
  - pestañas "Próximas" y "Pasadas" (filtrando `GET /api/reservas` por `beneficiarioId`, sin endpoint
    nuevo);
  - "Tutores autorizados", con la acción "No confío en este Tutor";
  - pedidos pendientes de ese hijo.
- Desde cada tarjeta de "Mis chicos", "Ver todo".

**Tests:** `listarAutorizaciones_soloDelPropioMenor` (otro AR recibe 404),
`menorNoPuedeListarAutorizaciones_403`, Playwright `MENORES-E2E-00x`.

**Tamaño:** S · **ADR/Spec:** no (US-2 y US-5 de M1 ya lo cubren).

---

## 8. Gestión de Admins

**Confirmación:**
- Los Admins se crean por SQL (`RUNBOOK_produccion.md` §2.5).
- `admin.admins` asocia un `usuario_id` a un rol (`Admin.java`) y **no hay superadmin**.
- **Hallazgo extra:** `ColasModeracionController.resolverCredencial` (l. 181) y
  `CredencialService.marcarAprobada` no chequean que el Admin no sea el propio Tutor. Nada impide que
  un Admin sea TUTOR y **apruebe su propia credencial**. Lo mismo vale para denuncias o alertas que lo
  involucren.

**¿Alcanza SQL para el piloto?** **Sí**, con 1 a 3 Admins y el RUNBOOK. Pero hay que cerrar ya el
conflicto de interés (S):
- `AdminModeracionGate.requiereAdmin`: exige que el usuario sea `tipo == ADULTO`, con el mismo
  fail-closed.
- Guarda "no resolver casos propios" en las credenciales, las denuncias (denunciante o denunciado),
  las alertas (detectado o participante) y la cola financiera (transacciones propias). Si no, 403.
- RUNBOOK: agregar "desactivar Admin" (`UPDATE ... SET activo=false`) y "cambiar rol".

**Pantalla mínima (después del piloto, M):**
- Migración: columna `admin.admins.gestiona_equipo BOOLEAN NOT NULL DEFAULT false`. El primero se
  prende por SQL.
- `GET/POST/PATCH /api/admin/equipo`: alta por DNI de un usuario ADULTO que ya existe, cambio de rol,
  activar y desactivar.
- Reglas: no te podés desactivar a vos mismo, y tiene que quedar al menos un Admin activo por rol
  (409). Auditado por el interceptor.
- `/admin/equipo` con una tabla simple.

**Tests:**
- `adminTutor_noPuedeAprobarSuPropiaCredencial_403` (regresión, falla hoy);
- `adminNoAdulto_403`;
- `equipo_noPuedeDesactivarseASiMismo_409`;
- `equipo_ultimoDelRol_409`.

**Spec:** US nueva en M8 (solo si se hace la pantalla). **Pregunta:** ¿pantalla en el piloto o SQL
hasta el lanzamiento? Recomiendo SQL más las guardas ya.

---

## 9. Especialidades del Tutor y verificación de credenciales (implementación de ADR-M1-06)

### 9.1 Estado verificado y un problema de base
- Hoy `activo_para_matching` significa **tres cosas a la vez**:
  1. "tiene credencial aprobada" (`CredencialService.marcarAprobada`, l. 114-116);
  2. "no está sancionado" (`SancionListeners`, l. 83);
  3. "no está bloqueado por kill-switch" (`SesionService.ramaMenor`, l. 547, **sin** cambiar
     `estadoCuenta`).

  La decisión (b), que pide que los Tutores declarados aparezcan, **no se puede implementar prendiendo
  el flag en masa**: rehabilitaría a Tutores bloqueados por una alerta pendiente. Además hoy
  `ReactivacionCuentaJob` (l. 50) y `AlertaSeguridadService` (l. 128) lo ponen en `true` aunque el
  Tutor nunca haya tenido credencial aprobada.
- La restricción `CHECK` de `tipo_documento` (V2) solo admite `TITULO`, `CERTIFICADO_ANALITICO` y
  `MATRICULA`.
- `OcrService` es específico del DNI. No hay OCR de texto libre ni lectura de PDF.

### 9.2 Modelo de datos (migraciones nuevas)
**`identidad.especialidades_tutor`**

| Columna | Definición |
|---|---|
| `id` | UUID PK |
| `tutor_id` | FK |
| `materia` | VARCHAR(100) |
| `nivel_maximo` | CHECK IN ('primario','secundario','universitario') |
| `estado` | CHECK IN ('DECLARADA','RESPALDO_DOCUMENTAL','VERIFICADA') DEFAULT 'DECLARADA' |
| `credencial_id` | NULL FK a `credenciales_academicas` |
| `created_at`, `updated_at` | |
| Restricción | UNIQUE (`tutor_id`, `materia`) |

- Entre 1 y 3 por Tutor, controlado en el servicio.
- `(materia, nivel)` se valida contra `matching.materias_niveles` por un puerto in-process de M2. No
  hace falta una FK entre schemas.

**`identidad.credenciales_academicas` (ALTER)**
- Se reemplaza el CHECK de `tipo_documento` por uno con los tipos nuevos (`SECUNDARIO`,
  `TERCIARIO_NO_DOCENTE`, `PROFESORADO`, `UNIVERSITARIO`, `ALUMNO_UNIVERSITARIO`,
  `MATRICULA_PROFESIONAL`, `OTRO`) **más los tres viejos, que quedan solo para filas históricas**. El
  API ya no los acepta.
- Columnas nuevas:
  - `institucion`, `denominacion`, `anio` (SMALLINT) y `enlace_verificacion` (VARCHAR(500));
  - `triaje` CHECK IN ('OK','NOMBRE_NO_COINCIDE','SIN_LECTURA'): marca para el Admin, no rechaza;
  - `metodo_verificacion` CHECK IN ('REGISTRO_OFICIAL','SOLO_DOCUMENTO');
  - `rechazo_codigo` (VARCHAR(40)), `rechazo_detalle` (VARCHAR(500)) y `rechazo_automatico` (BOOLEAN).
- El consentimiento para consultar registros usa `identidad.aceptaciones_clausula` (T08) con una
  cláusula nueva.
- **Minimización:** no se guarda el texto del OCR, solo el resultado del triaje.

**Migración de lo que ya existe**
- Las credenciales `APROBADO` quedan con `metodo_verificacion = 'SOLO_DOCUMENTO'` (lo dice ADR-M1-06).
- Para que nadie desaparezca del matching, a cada Tutor con temas se le **derivan hasta 3
  especialidades `DECLARADA`**: materia con más temas y el nivel máximo entre sus temas. El Tutor las
  confirma o edita en el checklist.
- Las credenciales viejas no se atan a ninguna especialidad. El Admin puede re-verificarlas.

**Visibilidad en el matching (decisión b), separando los significados**
- `activo_para_matching` pasa a significar **solo "no bloqueado"**, que es lo que escriben M9 y M3.
  `marcarAprobada` deja de tocarlo y `registrarTutor` lo pone en `true`.
- Consulta de candidatos (`UsuarioRepository.tutoresActivosParaMatching`):
  `TUTOR ∧ ACTIVA ∧ activo_para_matching ∧ foto ∧ ≥1 especialidad`.
- Migración: `UPDATE usuarios SET activo_para_matching = true WHERE tipo='TUTOR' AND
  estado_cuenta='ACTIVA'`, **excluyendo** a quien tenga una alerta
  `pendiente_revision`/`resuelta_baja` como `detectado_id` o una sanción vigente.

### 9.3 Backend
| Pieza | Diseño |
|---|---|
| Especialidades | `GET/PUT /api/tutores/me/especialidades` (solo TUTOR; lista completa de 1 a 3). Si se edita la materia o el nivel de una especialidad `VERIFICADA`, vuelve a `DECLARADA` |
| Carga de credencial | `POST /api/tutores/me/credenciales` (multipart): `tipo`, institución, denominación, año, `enlaceVerificacion` (obligatorio para digitales y alumno regular), `especialidadIds` que pretende respaldar y `aceptaClausulaRegistro`. Si no acepta la cláusula, 422 |
| Triaje (síncrono, sin costo) | 1) Tipo real por magic bytes y tamaño (como hoy). 2) Texto: los PDF con capa de texto se leen con **PDFBox** (dependencia Apache nueva, no es proveedor, sin ADR); las imágenes y los PDF escaneados van por el Tesseract de ADR-M1-01, con un puerto nuevo `OcrTextoLibre`. 3) **Rechazo inmediato** (con código visible) si no hay texto legible (`SIN_TEXTO`), si parece un DNI (`ES_DNI`: patrones "DOCUMENTO NACIONAL DE IDENTIDAD" o MRZ, que `DniParser` ya reconoce), si no tiene ninguna palabra de título o certificado (`NO_ES_TITULO`: diccionario "título/diploma/certificado/egres/otorga/universidad/instituto/profesor/matrícula") o si el enlace está fuera de la allowlist (`ENLACE_NO_OFICIAL`: https y `registrograduados.siu.edu.ar`, `*.argentina.gob.ar`, `mi.argentina.gob.ar`, `*.edu.ar`, lista en config). 4) **Marca** `NOMBRE_NO_COINCIDE` usando `coincideAproximado` (hay que extraerlo de `UsuarioService` a una clase compartida). 5) Prellenado opcional de institución y año |
| Cola del Admin | `GET /api/admin/moderacion/credenciales` agrega el **DNI** y el nombre verificados del Tutor, los datos declarados, el triaje, el enlace (se abre en una pestaña nueva, **sin scraping**) y un **paso a seguir según el tipo** (texto fijo por tipo, tabla del spike §4.2) |
| Resolución | `POST .../credenciales/{id}/resolver`: `{decision, metodo: REGISTRO_OFICIAL/SOLO_DOCUMENTO, especialidadesCubiertas[], rechazoCodigo, rechazoDetalle}`. Si aprueba, las especialidades cubiertas pasan a `VERIFICADA` (registro oficial) o `RESPALDO_DOCUMENTAL` (solo documento). Validación de la tabla §4.3 del spike: por ejemplo, `SECUNDARIO` solo cubre `primario`; si se intenta más, 422 |
| Rechazo automático | **No consume intento** del ciclo de 3 (FR-ID-012), para que un OCR malo no bloquee a un Tutor honesto. Rate limit por Tutor. Lo decide el dueño |
| Perfil y búsqueda | `TutorPerfilResponse.verificado` (booleano) se reemplaza por `especialidades: [{materia, nivel, estado}]`. `BusquedaResponse` suma las especialidades, para mostrar la insignia en la tarjeta sin pedir el perfil entero |

### 9.4 Frontend
- Onboarding del Tutor, en el checklist de UX-06:
  - "Tus especialidades" (1 a 3, con materia del catálogo y nivel máximo);
  - "Respaldá tus especialidades": carga de credencial guiada por tipo, con ayuda de dónde sacar el QR
    o enlace, la cláusula de consentimiento y el rechazo automático mostrado en el momento con su
    motivo.
- Perfil y resultados: insignias por especialidad.
  - "Matemática · secundario ✓ Verificado en registro oficial".
  - "· Documento revisado".
  - "· Declarada".
  - "Título verificado" (`tutores/[id]/page.tsx:155`) y el sello genérico de `Avatar` dejan de usarse.
  - Texto fijo: "Las recomendaciones ordenan resultados; vos decidís" (decisión a).
- Tarjeta del Admin (`/admin/credenciales`):
  - DNI y nombre del Tutor;
  - datos declarados;
  - triaje, con la marca en rojo si el nombre no coincide;
  - botón "Abrir enlace oficial";
  - paso a seguir;
  - selector del método;
  - casillas de especialidades cubiertas;
  - motivo de rechazo (lista cerrada más texto).

### 9.5 Tests (uno por US, más los casos borde)
- Rechazo automático: `triaje_esDni_rechazoInmediatoConMotivo`,
  `triaje_enlaceFueraDeAllowlist_rechazo`, `triaje_sinTexto_rechazo`,
  `rechazoAutomatico_noConsumeIntento`.
- Marca para el Admin: `triaje_nombreNoCoincide_marcaNoRechaza`.
- Resolución: `aprobar_registroOficial_especialidadVerificada`,
  `aprobar_soloDocumento_respaldoDocumental`, `secundarioNoCubreSecundario_422`.
- Visibilidad: `tutorSoloDeclarado_apareceEnMatchingMarcado`, `tutorSinEspecialidad_noAparece`.
- Migraciones:
  - `migracion_noReactivaTutorConAlertaPendiente`: regresión del problema de 9.1, tiene que fallar si
    se prende el flag en masa;
  - `migracion_legacyAprobada_quedaSoloDocumento`;
  - `migracion_derivaEspecialidadesDesdeTemas`.
- Datos: `adminVeDniEnCola`, `perfilNoExponeDni`.
- Consentimiento: `sinClausula_422`.

**Tamaño:** L. Chunks E1 a E4 del spike: E1 migraciones y Spec (M), E2 backend (M), E3 frontend (M),
E4 tests (S). **ADR:** ADR-M1-06, **pero hay que commitearlo** (hoy está untracked). **Spec:** M1 (US-4,
BR-ID-01, FR-ID-025 con el nuevo significado de `activo_para_matching`) y M2 (candidatos e insignias).

**Preguntas:**
- Texto de la cláusula (asesoría legal). Sin él, la carga nueva queda apagada con un flag, igual que
  el adicional de T08.
- ¿El rechazo automático consume intento? Recomiendo que no.
- ¿Se derivan especialidades de los temas para los Tutores existentes?
- ¿Allowlist cerrada o con `*.edu.ar` genérico?
- Tabla de "qué respalda qué": ¿se congela la del spike §4.3?

---

## 10. T-M3-06 — Kill-switch en el cliente (resumen)

**Estado:**
- **Decidido:** NSFWJS sobre TF.js, on-device (ADR-M3-01), y buffer de 30 s validado en el spike.
- **Hecho en el backend:** `POST /api/sesiones/{id}/killswitch` (la rama la decide el backend,
  T-M3-07), evidencia (T-M3-08), confirmación de adultos (T-M3-09), cierre de sala (AUD-001),
  alertas y pausa del escrow.
- **Falta en el cliente:** en `app/aula/[id]/page.tsx` no aparecen ni el clasificador, ni
  `killswitch`, ni la evidencia. En `package.json` no hay `nsfwjs` ni `@tensorflow/tfjs`. T02 (CAP)
  está **cerrado**, así que **T-M3-06 es lo único que frena `tinku.menores.sesiones-habilitadas`**.

**Lo que queda:**
1. Servir el modelo desde el propio dominio (hoy está en `spikes/nsfw-classifier/models/`, ignorado por
   git).
2. Clasificar con throttling (cada 250 a 500 ms) el video **del Tutor**, en el cliente del menor o
   del alumno. Umbral y confirmación por N frames seguidos.
3. Buffer rotativo de 30 s con MediaRecorder, **en memoria y descartado** salvo disparo (Tabla de
   Tiempos).
4. Al disparar se hace lo siguiente:
   - se llama a `/killswitch` con `detectadoId`;
   - en la rama menor, corte inmediato de la UI **sin preguntarle nada al menor** (Art. II);
   - en la rama adultos, blur y la pregunta al otro participante;
   - se sube la evidencia.
5. Fallback: si el dispositivo no puede correr el modelo, **fail-closed para clases con menores** (no
   se entra a la sala) y aviso para adultos.
6. Benchmark en un dispositivo real de gama media (pendiente de ADR-M3-01).
7. Tests: Playwright con un clasificador mockeado (disparo en la rama menor, que corta sin pregunta),
   más la prueba manual en el dispositivo.

**Riesgos:**
- Falsos positivos que cortan clases (ADR-M3-02 los acepta para Art. II).
- Falsos negativos: el clasificador de imágenes no ve todo. La denuncia de M9 sigue siendo la red.
- Rendimiento en celulares de gama baja.
- Un cliente manipulado puede **no** disparar. Nada del backend lo compensa en tiempo real, por eso
  la regla de no depender del menor y la denuncia posterior. Hay que declararlo como riesgo aceptado
  antes de prender el flag.

**Tamaño:** L.

---

## 11. Plan priorizado (chunks, un branch cada uno, `chunk/{id}`)

| Prioridad | Chunk | Contenido | Tamaño | Depende de |
|---|---|---|---|---|
| **P0** | `chunk/rev-seg-roles` | Punto 1 completo, más las guardas de Admin del punto 8 (tipo ADULTO, no resolver casos propios) | M | — |
| **P0** | `chunk/rev-pagos-conciliacion` | Punto 5: tabla de preferencias, búsqueda por `external_reference`, conciliación en el timeout, barrido, expiración de la preferencia y exclusión de ticket/atm | M | Filas de la Tabla de Tiempos |
| **P0** | `chunk/rev-pagos-modelo` | ADR-M5-02, corrección de javadocs y Spec M5. **B:** datos de cobro, `liquidaciones` y cola del Admin. **A:** OAuth, cifrado, refresh y token por llamada | B: M · A: L | Decisión A/B y contador |
| P1 | `chunk/rev-pagos-adicional` | Punto 4: outbox del reembolso del adicional y su cola | M | `rev-pagos-modelo` (la guarda depende de A/B) |
| P1 | `chunk/rev-cobros-tutor` | Punto 3: `mis-cobros` y `/cuenta/cobros` | M | `rev-pagos-modelo` |
| P1 | `chunk/esp-e1..e4` | Punto 9: especialidades y credenciales (primero commitear ADR-M1-06 y corregir el FR-ID-030 duplicado) | L | Cláusula legal (E2/E3 detrás de un flag) |
| P2 | `chunk/rev-reservar-409` | Punto 6: `codigo` en los errores, E2E del 409, limpieza y Tasks | S | — |
| P2 | `chunk/rev-ar-hijos` | Punto 7: listado de autorizaciones y `/cuenta/menores/[id]` | S | — |
| P3 | `chunk/m3-killswitch-cliente` | Punto 10 (T-M3-06) | L | Benchmark en dispositivo |
| P3 | `chunk/m8-equipo` | Punto 8, pantalla de equipo (solo si se aprueba) | M | Spec M8 |

Números de migración tentativos, se asigna el siguiente libre al mergear:
- V37: roles.
- V38: preferencias de pago.
- V39: modelo de pagos (A o B).
- V40: adicional.
- V41 a V43: especialidades.

Cada chunk:
- tilda en `Tasks_Tinku_Implementacion.md` **y** `Tasks_Tinku_Chunks.md` en el mismo commit;
- corre la suite con JDK 21 (baseline de 455 tests);
- lleva su test de regresión en rojo antes del fix.

---

## 12. Decisiones del dueño del producto

1. **Pagos: Opción A (OAuth por Tutor) o B (recaudación centralizada y transferencias manuales).**
   Recomendación: B en el piloto y A antes del lanzamiento, con el disparador escrito en ADR-M5-02.
2. **Consultar a un contador** sobre B (ingreso bruto, mandato, retenciones de MP, Monotributo) antes
   de cobrar plata real.
3. Cadencia de las transferencias a Tutores (propuesta: semanal) y monto mínimo. Van a la Tabla de
   Tiempos.
4. Conciliación de pagos: frecuencia N y ventana H (propuesta: 5 min y 48 h). Van a la Tabla de
   Tiempos.
5. Reintentos del reembolso del adicional: ¿se reusa el backoff 5/15/60 min y 3 intentos? Va a la
   Tabla de Tiempos.
6. Tutor que además es madre o padre: ¿se mantiene la exclusión del MVP (Spec M1)? Recomendación: sí.
7. Si en prod hay menores a cargo de un Tutor o autorreservas: ¿baja o reasignación?
8. "Mis cobros": ¿el Tutor ve el motivo de una pausa? Recomendación: no, solo "en revisión".
9. ¿Se acepta el picker actual como cierre de T-M4-13/14?
10. Admins: ¿alcanzan SQL y las guardas en el piloto, o se hace la pantalla `/admin/equipo`?
    Recomendación: SQL y guardas.
11. Especialidades:
    - texto de la cláusula de consentimiento (legal);
    - ¿el rechazo automático consume intento? (recomendación: no);
    - ¿se derivan especialidades de los temas para los Tutores existentes? (recomendación: sí);
    - allowlist de dominios;
    - tabla "qué credencial respalda qué nivel".
12. Kill-switch en el cliente:
    - ¿se acepta el riesgo del cliente manipulado (que no dispara) como riesgo declarado antes de
      habilitar menores?
    - ¿con fail-closed en dispositivos que no pueden correr el modelo?
13. Commitear `ADR-M1-06` y los cambios de FR-ID-028 y FR-ID-030 que están sin commitear, y resolver
    el **FR-ID-030 duplicado** en Spec M1.
