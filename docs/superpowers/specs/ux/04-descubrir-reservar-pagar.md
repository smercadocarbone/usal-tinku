# UX-04 — Descubrir, reservar y pagar (el embudo del negocio)

**Branch:** `ux/embudo` · **Precondición:** UX-01, UX-02 y **FASE2-01** (bloques de 30 min y tarifa
por hora) mergeadas · **Partes bloqueadas por:** **U1** (bio/foto), **U2** (búsqueda pública).
Capturas "antes": `capturas-antes/estudiante__buscar*`, `*tutores_ID*`, `*reservar*`, `*pagar*`,
`menor__reservar*`.

Es la spec más importante de UX: de acá sale cada peso que entra a la plataforma.

---

## 1. Buscar (`/buscar`)

**Hoy:**
- Sin búsqueda escrita, la pantalla queda **vacía** (no muestra ningún tutor).
- **La primera búsqueda tardó 34 s** (medido); las siguientes, 0.1 s. Es la carga en frío del modelo
  de embeddings en `matching-service`.
- Tarjeta de resultado: nombre, iniciales y un enorme **"A consultar"** (precio desconocido) como
  elemento principal. Sin materias, calificación, sello de verificado ni disponibilidad.
- Los chips de nivel no muestran cuál está activo. "Guardar esta búsqueda" aparece antes que los
  resultados, con más peso que ellos.

**Propuesta:**
- **Estado inicial útil:** tutores destacados o recomendados (por materias más pedidas) antes de
  escribir nada, y las búsquedas guardadas como atajos.
- **Buscador + filtros:** texto libre (búsqueda semántica, se mantiene) + filtros explícitos: nivel,
  materia, rango de precio por hora y "disponible esta semana". Chips con estado seleccionado claro
  (`aria-pressed`). En mobile, los filtros van en una hoja inferior ("Filtros · 2").
- **Tarjeta de tutor (nueva):** `Avatar` (foto si U1), nombre, sello "Identidad y título
  verificados" (solo si la credencial está aprobada), materias principales (máx. 3 + "y 2 más"),
  `Estrellas` + cantidad ("4.8 · 12 clases"; "Tutor nuevo" si hay menos de 5 calificaciones,
  FR-REP-007), **precio por hora** con `Precio`, y "Próximo horario: jue 25 · 18:00". Toda la tarjeta
  es clickeable (`Tarjeta interactiva`).
- **Ordenar:** relevancia (default), precio y calificación.
- **Resultados vacíos:** "No encontramos tutores para '…'. Probá con otras palabras o sacá filtros."
  con los filtros activos removibles.
- Si hubo búsqueda y el rol es Adulto Responsable, la marca de "no autorizado para tu hijo" que ya
  existe (`noAutorizado`) se muestra como una insignia con acción "Autorizar".

**Backend:**
- **Warm-up de `matching-service`:** cargar el modelo en el arranque del servicio (evento de startup
  de FastAPI), no en la primera request. El primer usuario después de cada reinicio no puede esperar
  34 s. Test: el `/health` responde "listo" recién con el modelo cargado.
- **Evitar el N+1:** hoy `POST /api/matching/...` devuelve solo `{tutorId, score, noAutorizado}` y el
  frontend pide cada perfil por separado. Sumá a la respuesta de búsqueda los datos de la tarjeta
  (nombre, materias, calificación, cantidad, precio por hora, verificado, próximo horario), o un
  endpoint de "resumen de tutores por ids" en una sola llamada.

## 2. Perfil del tutor (`/tutores/[id]`)

**Hoy** (`estudiante__tutores_ID__desktop.png`): nombre y "Calificacion: Sin calificaciones
suficientes" y nada más. **"Denunciar" en rojo junto a "Reservar clase"**, con casi el mismo peso.
"Sesion de Adulto" (jerga) debajo del botón. Es la pantalla que más confianza tendría que generar.

**Propuesta:**
- **Encabezado:** avatar grande, nombre, sellos de verificación (identidad + título, con el tipo de
  documento: "Título verificado"), calificación, cantidad de clases dadas, precio por hora.
- **Sobre mí** (bio, **U1**). Si U1 = no, esta sección no existe (no dejar un hueco).
- **Materias y niveles** que enseña, agrupados.
- **Disponibilidad:** la próxima semana en formato compacto, con CTA a elegir horario.
- **Opiniones:** las calificaciones públicas con texto, si existen (FR-REP).
- **CTA fija:** en mobile, barra inferior con precio + "Reservar clase"; en desktop, tarjeta
  lateral fija.
- **Denunciar** pasa a un menú secundario ("⋯ → Reportar este perfil"), abre `FormularioDenuncia`
  en un modal y explica qué pasa después. Nunca al lado del CTA principal.
- Para un **menor**: el CTA es "Pedir esta clase" (solicitud al Adulto Responsable, ver §3).

**Backend:** `TutorPerfilResponse` hoy tiene materias, nivel y calificación. Sumar: precio por hora,
cantidad de clases dadas, verificado (credencial aprobada) y tipo de documento verificado; bio y
foto si U1. **Sacar** `capacidadEstudiante` y `capacidadAdultoResponsable`, que no le sirven a quien
mira un perfil público.

## 3. Reservar (`/reservar?tutor=…`)

**Hoy** (`estudiante__reservar_tutor_ID__desktop.png`):
- Lista de franjas crudas con segundos ("Martes de 09:00:00 a 10:00:00"), **mezclando recurrentes y
  puntuales** y mostrando **fechas pasadas** (12/09 y 13/09 con hoy 23/09), sin orden.
- La fecha y la franja se eligen por separado sin relación visible.
- **No muestra precio** antes de "Reservar y pagar", ni deja elegir duración. Sin resumen.
- Para el **menor**: "Tu Adulto Responsable debe reservar por vos" y fin (UX-02 B5).
- `components/DynamicTimeSlotPicker.tsx` existe y resuelve buena parte, pero no está conectado
  (T-M4-13 a T-M4-15).

**Propuesta — reserva en pasos (`Pasos`):**
1. **Cuándo:** calendario de 14 días con los días que tienen disponibilidad marcados; al elegir un
   día, los horarios de inicio en bloques de 30 min (`GET /api/tutores/{id}/horarios?fecha=&duracionMinutos=`,
   ya existe). Nunca fechas pasadas ni horarios dentro de la ventana mínima (FR-RES-013).
2. **Cuánto:** duración en múltiplos de 30 min (30, 60, 90… hasta lo que permita la franja, D6),
   con el **precio calculado en vivo** (`precioHora × unidades / 2`).
3. **Para quién** (solo Adulto Responsable): su propio uso o uno de sus hijos, con aviso si ese hijo
   todavía no autorizó a este tutor (y la acción para autorizarlo).
4. **Resumen:** tutor, día y hora (`FechaHora` largo), duración, precio, y **cómo funciona el pago**
   en una línea ("Pagás ahora; el dinero queda retenido y se le libera al tutor 24 hs después de la
   clase"). CTA "Confirmar y pagar".
- **Menor:** mismos pasos 1 y 2, y el CTA es "Enviarle el pedido a mi adulto responsable"
  (solicitud, ya soportada por el backend). Pantalla de éxito: "Le avisamos a tu adulto responsable.
  Vas a ver la clase en Mis clases cuando la apruebe."
- Reutilizá y adaptá `DynamicTimeSlotPicker` en lugar de escribir otro.
- **Conflictos:** si el horario se ocupó mientras el usuario decidía (409), mensaje claro y volver
  al paso 1 con ese horario marcado como tomado.

## 4. Pagar (`/pagar?reserva=…`)

**Hoy:** sin parámetro, error rojo con un "Reintentar" que no hace nada (UX-02 B9). Sin cabecera.

**Propuesta:**
- Resumen de la reserva + cuenta regresiva del **tiempo para pagar** (FR-RES-020, el timeout de pago
  de la Tabla de Tiempos: no inventes el número, leelo del backend o de la tabla).
- Botón a MercadoPago; en **Modo Bypass**, un aviso claro de que la reserva se confirma sin cobro
  (entorno de prueba).
- Pantallas de vuelta de MercadoPago: **aprobado** (confirmación, agregar a calendario, ir a Mis
  clases), **pendiente** ("MercadoPago está procesando el pago…"), **rechazado** (reintentar con otro
  medio) y **reserva vencida** ("El tiempo para pagar se agotó y el horario se liberó").

## Criterios de aceptación

- Recorrido completo contra el backend real en el stack local: buscar → perfil → reservar 60 min →
  pagar en Modo Bypass → la reserva aparece confirmada en Mis clases. Y el del menor: pedir clase →
  el AR la ve y la aprueba en su cuenta.
- Capturas "después" de todas las pantallas, desktop y mobile, incluyendo estados vacío, error y
  conflicto.
- Primera búsqueda después de reiniciar `matching` en menos de 2 s (medido de la misma forma que en
  la auditoría).
