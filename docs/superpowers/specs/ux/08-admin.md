# UX-08 — Panel de administración

**Branch:** `ux/admin` · **Precondición:** UX-01 y UX-02 (B10: `GET /api/admin/yo`) mergeadas.
Capturas "antes": `capturas-antes/admin__*`.

## 1. Estructura

**Hoy:** menú lateral con todas las secciones para cualquier rol (el de Moderación entra a "Pagos
fallidos" y "Salud" y le sale "No tenés permiso"); `/admin` redirige directo a Alertas; sin
contadores; subtítulo para desarrolladores ("cada cola valida el permiso del lado del servidor").

**Propuesta:**
- **Menú según el rol** (`GET /api/admin/yo`): Moderación y Seguridad → Alertas, Denuncias,
  Credenciales; Soporte Financiero → Pagos, Precios, Tickets. Salud del sistema, para quien
  corresponda según el backend.
- **Contadores de pendientes** en cada ítem del menú y **urgencia**: una Alerta de kill-switch tiene
  12 hs de ventana de revisión (Tabla de Tiempos); mostrar las que están por vencer en rojo con el
  tiempo restante.
- **`/admin` = tablero:** por cada cola del rol, cuántas hay, la más urgente y el tiempo restante de
  su plazo. Nada de gráficos decorativos.
- **Banner persistente de Modo Bypass** en todo `/admin` cuando la pasarela está apagada (FASE2-07).

## 2. Colas (patrón común)

Todas las colas comparten un mismo patrón, en vez de resolverse cada una distinto:
- **Lista** ordenada por urgencia (ya lo hace el backend) con: sujeto, tipo, plazo restante, estado.
- **Detalle en un panel lateral** (desktop) o una pantalla (mobile), con toda la evidencia y el
  historial.
- **Acciones con consecuencia explícita** y `ModalConfirmacion`: el texto del modal dice qué va a
  pasar ("Vas a rechazar la credencial de Jorge Martínez. Es su intento 2 de 3; si la rechazás, puede
  volver a intentar mañana."). **Rechazar pide motivo** (se le muestra al tutor).
- Después de resolver, el ítem sale de la lista con un `Toast` y "Deshacer" **solo** si la acción es
  reversible en el backend (si no lo es, no ofrecer deshacer).

## 3. Por cola

- **Credenciales** (`admin__admin_credenciales__desktop.png`): el visor de documento de FASE 1
  (AUD-007) se integra al panel de detalle, al lado de los datos del tutor (nombre, DNI verificado,
  tipo de documento, intentos), para comparar sin cambiar de pantalla.
- **Alertas de seguridad:** qué pasó (rama menor/adultos), quién fue el detectado, el estado de la
  suspensión preventiva, el clip de evidencia (FASE2-09, con las mismas cabeceras seguras), el
  descargo si lo hay, y las dos decisiones (`reactivar` / `sancionar`) explicando **qué pasa con la
  plata** en cada una (ADR-M3-02: en los dos casos se reembolsa al estudiante).
- **Denuncias:** denunciante **anónimo para el denunciado** (FR-SEC-006) pero visible para el Admin;
  plazo de descargo (48 hs) y SLA (5 días hábiles); resolución `infundada`/`fundada`/`escalada` con lo
  que implica cada una para el escrow.
- **Pagos fallidos, precios, tickets y salud:** aplicar el patrón común y el sistema visual; revisar
  cada pantalla con un usuario de Soporte Financiero (la auditoría las vio solo con Moderación).

## Criterios de aceptación

- Capturas "después" con un usuario de **cada** rol de admin, desktop y mobile.
- Ningún rol ve secciones que no puede usar.
- Toda acción irreversible pide confirmación con la consecuencia escrita.
