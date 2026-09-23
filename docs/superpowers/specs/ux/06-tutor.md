# UX-06 — Experiencia del tutor: onboarding, agenda, materias y precio

**Branch:** `ux/tutor` · **Precondición:** UX-01, UX-02, **FASE2-01** (bloques de 30 min y precio
por hora) y **FASE3-03** (el `sub` del JWT deja de ser el DNI) · **Parte bloqueada por:** **U1**.
Capturas "antes": `capturas-antes/tutor__*`.

---

## 1. Onboarding: "qué me falta para recibir alumnos"

**Hoy** (`tutor__cuenta__desktop.png`): el perfil muestra DNI y "Capacidad Estudiante/Adulto
Responsable: Inactiva" (irrelevante para un tutor) y un aviso "Tu credencial está en revisión".
**El aviso es engañoso:** Jorge ya tiene una credencial **aprobada** y además subió otra; el banner
mira solo la última. El tutor **no sabe qué le falta** para aparecer en las búsquedas.

**Propuesta — checklist en la home del tutor ("Mi agenda"):**
1. Identidad verificada ✓ (del registro).
2. Credencial académica: pendiente / en revisión / aprobada / rechazada (con el motivo y cuándo
   puede reintentar, FR-ID-008/012). Si tiene **una aprobada y otra en revisión**: "Tu perfil está
   verificado. Tu nueva credencial está en revisión."
3. Materias cargadas.
4. Horarios publicados.
5. Precio por hora definido.
6. Bio y foto (**U1**).
Con barra de progreso y, al completar, "Tu perfil ya aparece en las búsquedas" + link a ver su
perfil público como lo ve una familia.

**Backend:** un endpoint `GET /api/tutores/me/estado-perfil` que devuelva cada ítem y si el tutor
es visible en el matching (la regla vive en el backend; el frontend no la reimplementa).

## 2. Mi agenda (`/cuenta/horarios`)

**Hoy** (`tutor__cuenta_horarios__desktop.png`): la grilla semanal para pintar bloques es un buen
concepto, pero **no carga las franjas existentes** (UX-02 B4: pide por DNI → 403), muestra "El
listado de franjas está pendiente en backend", se corta a las 20:00, el botón "Guardar" se superpone
con la grilla y la instrucción aparece dos veces.

**Propuesta:**
- **Dos vistas:** "Mi semana tipo" (disponibilidad recurrente) y "Calendario" (las próximas semanas
  con puntuales, excepciones y **las clases ya reservadas** encima, que no se pueden borrar).
- Grilla en bloques de **30 min** (D6), de 07:00 a 23:00 con scroll interno; pintar con clic o
  arrastre (se conserva la interacción actual), y en mobile, tocar bloques con una lista por día en
  vez de la grilla completa.
- **Guardado claro:** barra fija "3 cambios sin guardar · Guardar · Descartar". Los cambios se
  validan contra FR-RES-024 (30 a 180 min por franja) y se explica si una franja se partió.
- No se puede borrar un bloque que tiene una clase reservada: se muestra bloqueado, con la clase.

## 3. Mis materias (`/cuenta/materias`)

**Hoy** (`tutor__cuenta_materias__desktop.png`): un árbol de acordeones de 3 niveles (Primario →
1° … 6° → temas), sin resumen de lo elegido. Dice "se guardan solos" sin confirmación visible.

**Propuesta:**
- Arriba, **lo que ya enseña** como chips removibles, agrupado por nivel.
- Abajo, el catálogo con buscador ("Buscá un tema…") y selección por nivel/año con "seleccionar todo
  el año".
- Autoguardado con `Toast` ("Guardado") y manejo de error (si falla, el chip vuelve atrás y avisa).

## 4. Precio (`/cuenta/precio`)

**Hoy** (`tutor__cuenta_precio__desktop.png`): campo en "0" con el `$` **a la derecha**, "precio por
sesión" (con D6 pasa a ser **por hora**), provincia por defecto "Buenos Aires" y un 404 de fondo
cuando no hay precio de referencia (UX-02 B11).

**Propuesta:**
- Campo `Precio` con el `$` adelante, "por hora".
- **Referencia** (M5-E): "En tu provincia, los tutores cobran entre $X y $Y por hora" cuando exista;
  si no, no mostrar el bloque (no un mensaje de "todavía no tenemos").
- **Vista previa** de lo que ve una familia: "Una clase de 1 h cuesta $X; de 30 min, $Y".
- Qué se lleva Tinku, **si** el backend lo expone (comisión de BR-PAG-01): mostrar cuánto recibe el
  tutor por hora. Si no está expuesto, pedirlo como cambio de backend.

## 5. Mis clases (tutor)

Misma estructura que `05-cuenta.md` §3, desde el lado del tutor: con quién es (nombre del alumno,
**sin** datos de contacto de un menor), materia, estado, "Entrar al aula", y cuánto cobra y cuándo se
libera el pago de cada clase.

## Criterios de aceptación

- Un tutor recién registrado entiende qué le falta y llega a "visible en búsquedas" sin ayuda.
- La agenda muestra las franjas existentes y las clases reservadas; se puede editar sin perder cambios.
- Capturas "después" de todas las pantallas, desktop y mobile.
