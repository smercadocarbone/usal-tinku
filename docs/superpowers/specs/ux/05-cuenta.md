# UX-05 — Cuenta: estudiante, Adulto Responsable y menor

**Branch:** `ux/cuenta` · **Precondición:** UX-01 y UX-02 mergeadas.
Capturas "antes": `capturas-antes/estudiante__cuenta*`, `adultoResponsable__cuenta*`,
`menor__cuenta*`.

---

## 1. Shell de cuenta

**Hoy:** menú lateral con "Perfil" siempre activo (UX-02 B10); "Denuncias y alertas" en la
navegación general de cualquier estudiante (suena alarmante); en mobile, el patrón menú → detalle
funciona pero la cabecera queda apretada.

**Propuesta:** la navegación principal pasa a la `Cabecera`/bottom nav (UX-01 §4) y la cuenta queda
para lo que es de la cuenta:

| Sección | Quién la ve | Contenido |
|---|---|---|
| Mis clases | todos | Próximas y pasadas (ver §3). Es la home del usuario logueado. |
| Mis chicos | Adulto Responsable | Ver §5. |
| Perfil | todos | Ver §2. |
| Seguridad y acceso | todos | Contraseña, sesiones (tras FASE3-03, "cerrar sesión en todos los dispositivos" = subir `credentials_version`). |
| Casos y reportes | solo si tiene alguno | Denuncias recibidas y alertas (hoy "Denuncias y alertas"). Si no tiene ninguno, **no aparece en el menú**. |
| Notificaciones | todos | La bandeja in-app de FASE2-03, con contador en la cabecera. |

## 2. Perfil

**Hoy** (`estudiante__cuenta__desktop.png`): DNI completo, "Tipo de cuenta", "Capacidad Estudiante:
Activa", "Adulto Responsable: Inactiva" y un recuadro de checkboxes "Capacidades". **No muestra ni
el nombre ni el email.**

**Propuesta:**
- Encabezado con `Avatar`, nombre y apellido, email y DNI **enmascarado** (`••.•••.233`).
- "¿Cómo usás Tinku?": en vez de checkboxes de "capacidades", dos interruptores explicados:
  "Tomo clases" y "Tengo hijos o hijas a cargo" (con lo que implica cada uno: activar el segundo
  habilita la sección Mis chicos). Guardado con `Toast`.
- Datos que el usuario puede cambiar y los que no (nombre y DNI vienen de la verificación: decilo).
- Para el **menor**: vista simplificada, con "Tu adulto responsable es Carlos P." y sin datos
  editables sensibles.

## 3. Mis clases (lista)

**Hoy** (`estudiante__cuenta_reservas__desktop.png`): fecha, estado en texto plano y precio. **No
dice con quién es la clase ni de qué materia.** "No se presentaron" suena acusador. No separa
próximas de pasadas.

**Propuesta:**
- Pestañas **Próximas** / **Pasadas** (y "Pedidos" para el menor y el AR: solicitudes pendientes).
- Tarjeta: `Avatar` + nombre del tutor, materia, `FechaHora` corto, duración, `EstadoReserva`
  (pastilla con ícono), y **la acción que corresponde al estado**:

| Estado | Acción principal |
|---|---|
| Pendiente de pago | "Pagar" (con el tiempo restante) |
| Confirmada, falta más de 5 min | "Ver detalle"; si falta poco, "Entrar al aula" destacado |
| En curso | "Entrar al aula" |
| Finalizada sin calificar (solo el pagador, D9) | "Calificar" |
| Cancelada / no realizada | ninguna (solo detalle) |

- Para el **Adulto Responsable**: cada tarjeta dice para cuál de sus hijos es ("Clase de Sofía").
- Vacío: "Todavía no tenés clases. Buscá un tutor para reservar la primera." con CTA.

## 4. Detalle de clase

**Hoy** (`*cuenta_reservas_ID*`): estado, fecha, horario, monto y, en canceladas, el enum
`timeout_pago`. Sin tutor, sin acciones.

**Propuesta:**
- Encabezado: tutor (link al perfil), materia, `FechaHora` largo, duración, para quién.
- **Línea de tiempo del estado:** reservada → pagada → confirmada → clase → pago liberado al tutor
  (o reembolsado), con fechas. Para canceladas, el motivo en lenguaje humano (UX-02 B6) y qué pasó
  con la plata ("Te devolvimos $15.000 el 20/9").
- **Acciones según estado** (la misma tabla de §3), más "Cancelar clase" con `ModalConfirmacion` que
  explique la política **antes** de confirmar (FR-RES-008/016: con más de 24 hs, reembolso total;
  con menos, se le paga al tutor; leé la política del backend, no la inventes), y "Reportar un
  problema" en un menú secundario.
- Si hay resumen automático de la clase (M6), mostrarlo acá cuando exista.
- **Backend:** `ReservaResponse` hoy manda solo ids. Sumar: nombre del tutor, nombre del
  beneficiario, materia (si la reserva la tiene), duración (FASE2-01) y **acciones disponibles
  calculadas en el servidor** (`puedePagar`, `puedeEntrar`, `puedeCalificar`, `puedeCancelar` con la
  consecuencia de cancelar ahora). Así la regla de negocio no se duplica en el frontend.

## 5. Mis chicos (Adulto Responsable)

**Hoy** (`adultoResponsable__cuenta_menores__desktop.png`): el formulario de alta de menor ocupa la
pantalla; los menores existentes **no se listan** (solo aparecen en un `<select>` para darlos de
baja); "Contrasena" sin tilde; la baja no advierte nada.

**Propuesta:**
- **Lista de hijos** como tarjetas: nombre, edad, próximas clases, **tutores autorizados** (con
  acción de revocar) y pedidos pendientes de ese hijo.
- **Pedidos pendientes** arriba, destacados, con "Aprobar y pagar" / "Rechazar" (el flujo de
  solicitudes ya existe en el backend).
- **Sumar un hijo o hija:** botón que abre el alta en pasos (datos → DNI con `SubidaArchivo` →
  acceso del menor → consentimiento). El consentimiento explícito se muestra completo y legible,
  con la versión del texto (ya se manda `versionTextoConsentimiento`).
- **Dar de baja:** dentro de la tarjeta del hijo, en un menú secundario, con `ModalConfirmacion`
  que explique qué pasa (tras FASE2-06: se anonimizan sus datos y no puede volver a entrar; si tiene
  clases futuras, cuáles se cancelan). El botón dice "Dar de baja a Sofía".

## 6. Casos y reportes

**Hoy** (`estudiante__cuenta_seguridad__desktop.png`): correcto en contenido; empty states secos.

**Propuesta:** solo aparece si hay algún caso (§1). Cada caso: qué es, estado, **plazo para el
descargo** con cuenta regresiva (48 hs, FR-SEC-010) y el formulario de descargo con contador de
caracteres (máx. 300). Tono neutro, sin culpar ("Recibimos un reporte sobre una clase. Podés contar
tu versión.").

## Criterios de aceptación

- Capturas "después" de todas las pantallas, desktop y mobile, para estudiante, AR y menor.
- Un AR puede, sin salir de su cuenta: ver a su hija, aprobar un pedido, autorizar un tutor y
  entender qué implica darla de baja.
- Ningún enum ni id visible. Suite del backend verde por los cambios de `ReservaResponse`.
