# FASE2-03 — Puerto de notificaciones, outbox y bandeja in-app (AUD-014)

**Branch:** `aud/fase2-p1-integridad` (sub-branch `aud/fase2-notificaciones` recomendado) ·
**Riesgo:** medio (funcionalidad nueva) · **Finding:** AUD-014 · **Decisión:** D2-bis ·
**Email bloqueado por:** **P5**.

## 1. Problema (verificado al 2026-09-22)

No existe infraestructura de notificaciones. Lo único parecido:
- `identidad/port/NotificadorResetPassword` + `NotificadorResetPasswordLog`, que desde AUD-008 solo
  loguea el id del usuario: **"olvidé mi contraseña" hoy no funciona** (fail-closed a propósito).
- `pagos/port/AlertaSoporteProveedor`, que abre un ticket interno para Soporte (no avisa a usuarios).
- Recordatorios de M6/M7 que son `log.info`.

Consecuencias concretas: el **Adulto Responsable no se entera de que se disparó un kill-switch**
en la clase de su hijo (`Spec_M3` US-6 lo exige), y el **denunciado no se entera** de que corre su
plazo de descargo de 48hs (FR-SEC-010).

## 2. Decisiones

- **Puerto** `Notificador` en `com.tinku.shared.notificacion`, **sin ninguna dependencia hacia
  módulos de dominio** (nada de `Usuario`, `Reserva`, etc. en su firma): recibe
  `UUID destinatarioId`, un `TipoNotificacion` (enum) y un `Map<String, String>` de datos. Así no
  se agrava el ciclo `shared ↔ admin` de AUD-019 (ver ADR-000-03).
- **Outbox transaccional:** la notificación se persiste **en la misma transacción** que el hecho que
  la origina. Si el hecho se revierte, la notificación también. Nunca "se mandó un aviso de algo
  que no pasó".
- **Canal inicial: bandeja in-app.** La tabla outbox **es** la bandeja: el usuario ve sus
  notificaciones al entrar. Cero costo, cero proveedor, cumple AUD-014 para usuarios con sesión.
- **D2-bis:** el aviso al Adulto Responsable es **inmediato e incondicional** y **no incluye el
  clip** ni describe el contenido detectado. El clip solo se habilita al resolverse `SANCIONAR`
  (eso es de FASE2-09).

## 3. PARAR (P5) — email

El reset de contraseña necesita un canal **fuera** de la app (el usuario no puede loguearse). Eso
exige elegir un proveedor de email (Resend, SES, Brevo, SMTP propio…) → **ADR previo** (A5). Esta
tarea **no** implementa email salvo que P5 esté resuelta. Si lo está: `docs/adr/ADR-000-06.md` y un
adaptador que lea el outbox y envíe, con reintentos por **Quartz persistido** (A4) usando el mismo
backoff 5/15/60min de la Tabla de Tiempos (agregá una fila propia para notificaciones).

## 4. Archivos

- **Nuevo:** `V<siguiente>__notificaciones.sql` → tabla `admin.notificaciones`:
  `id UUID PK`, `destinatario_id UUID NOT NULL FK identidad.usuarios`, `tipo VARCHAR(40) NOT NULL`,
  `datos JSONB NOT NULL DEFAULT '{}'`, `creada_at TIMESTAMPTZ NOT NULL DEFAULT now()`,
  `leida_at TIMESTAMPTZ NULL`, `enviada_email_at TIMESTAMPTZ NULL`; índice
  `(destinatario_id, creada_at DESC)`.
- **Nuevo:** `shared/notificacion/Notificador.java` (puerto), `TipoNotificacion.java` (enum).
- **Nuevo:** implementación en `admin` (`admin/notificacion/NotificadorOutbox.java` + entidad +
  repositorio), porque el panel de Admin la consulta.
- **Nuevo:** `admin/web/NotificacionesController.java` → `GET /api/notificaciones` (las del usuario
  autenticado, paginadas, más nuevas primero) y `POST /api/notificaciones/{id}/leida` (solo el
  destinatario; si no, **404**).
- Llamadores: `aula/SesionService` (ramas de kill-switch) y `seguridad/service/DenunciaService`
  (`presentar`).

## 5. Pasos

1. Migración, entidad, repositorio y puerto.
2. **Kill-switch rama menor** (`ramaMenor`): notificación `KILLSWITCH_MENOR` al **Adulto
   Responsable del beneficiario** con `{sesionId, fecha}`. Sin nombre del Tutor, sin contenido. Si
   el beneficiario no tiene AR cargado (no debería pasar: FR-ID-020), log `ERROR` y seguir: **nunca**
   abortar el corte por una notificación (Artículo II manda).
3. **Denuncia registrada** (`DenunciaService.presentar`): `DENUNCIA_RECIBIDA` al **denunciado** con
   `{denunciaId, descargoVenceAt}`. **Sin** id ni nombre del denunciante (FR-SEC-006).
4. Endpoints de la bandeja. Solo el destinatario ve o marca sus notificaciones.
5. **No** implementar recordatorios de calificación ni de resumen: siguen como `log.info`.
6. La UI de la bandeja es parte de las specs de UX (`docs/superpowers/specs/ux/`). Acá solo el
   backend; si hace falta, un contador mínimo en el header queda para la spec de UX.

## 6. Tests obligatorios (RED primero)

1. `killswitchMenor_notificaAlAdultoResponsable_sinDatosDelContenido`.
2. `killswitchMenor_siFallaLaNotificacion_elCorteIgualSePersiste` (mockeá el puerto para que
   lance; la sesión queda cortada).
3. `denunciaPresentada_notificaAlDenunciado_sinRevelarAlDenunciante` (el JSON de
   `GET /api/notificaciones` del denunciado no contiene el id del denunciante).
4. `bandeja_soloElDestinatarioVeYMarca` (otro usuario → 404).
5. `notificacion_seRevierteConLaTransaccion` (si `presentar` falla después de notificar, no queda
   notificación).

## 7. Criterios de aceptación

- Suite verde. `REGISTRO_FINDINGS.md` AUD-014 → `CERRADO` para el canal in-app. Si P5 no se
  resolvió, anotá que el reset de contraseña **sigue bloqueado** y por qué (no lo cierres "por
  análisis").
- `Spec_M3` US-6: escribir **qué** se le notifica al AR y qué puede ver después (hoy el Spec dice
  "se notifica" sin decir qué). Reemplazar las notas "NO IMPLEMENTADO (AUD-014)" que correspondan.
- `Spec_M9` FR-SEC-010: el aviso al denunciado.

## 8. NO tocar

- `NotificadorResetPassword`: queda como está hasta P5.
- `AlertaSoporteProveedor`: es un canal interno distinto y funciona.
