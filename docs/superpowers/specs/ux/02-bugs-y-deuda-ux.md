# UX-02 — Bugs funcionales y deuda de copy (antes de cualquier rediseño)

**Branch:** `ux/bugs-funcionales` · **Prioridad:** la más alta de todas las specs de UX.
Estos problemas se ven en la auditoría del 2026-09-23 (`capturas-antes/` y
`capturas-antes/errores-consola-y-red.json`). **No es rediseño:** es que la app funcione y no le
mienta al usuario. Cada ítem va en su propio commit.

Los ítems marcados **[backend]** siguen el protocolo de
`docs/superpowers/specs/remediacion/00-LEEME-opencode.md` (test RED, suite completa).

---

## B1 — Nadie puede registrarse como tutor desde la UI  🔴 crítico

**Evidencia (reproducido):** `POST /api/tutores/registro` sin `email` → rechazado; con `email` →
201. `RegistroTutorRequest` exige `@NotBlank @Email String email`, pero
`frontend/src/app/registro/tutor/page.tsx` **no tiene campo de email** y no lo manda en `datos`.
Resultado: **el alta de tutores está rota**. Nadie lo notó porque los E2E mockean `/api` (AUD-031).

**Qué hacer:** agregar el campo email (con `type="email"` y `autoComplete="email"`) al formulario y
al JSON de `datos`. Test E2E del registro de tutor que verifique que el body **incluye** `email`.
(El rediseño completo del registro está en `03-publico.md`: acá solo que funcione.)

## B2 — Los errores de validación le llegan al usuario como "no autorizado" (403)  🔴 [backend]

**Evidencia:** en el caso de B1, el log muestra `MethodArgumentNotValidException` resuelta por
`DefaultHandlerExceptionResolver`, y el cliente recibe **403**. Spring reenvía el error a `/error`,
que no está en ningún `permitAll` de `SecurityConfig`, así que la cadena de seguridad lo corta.
Cualquier formulario con un campo inválido le muestra al usuario "no autorizado".

**Qué hacer:**
1. Test RED: `registroTutor_sinEmail_responde400ConElCampo` (hoy 403).
2. Handler de `MethodArgumentNotValidException` que responda **400** con
   `{"error": "...", "campos": {"email": "es obligatorio"}}`, en español. Revisá si cada
   `*ExceptionHandler` scoped por módulo ya lo maneja (hay uno en `AdminExceptionHandler`); lo más
   simple es un `@RestControllerAdvice` global de menor precedencia, o permitir `/error` en
   `SecurityConfig` **y** tener un handler que arme el cuerpo. Elegí una y justificala.
3. El frontend muestra el error **junto al campo** (usa `detalles.campos` que `leerError` en
   `lib/api.ts` ya expone como `detalles`).

## B3 — Hidratación de React rota en todo `/cuenta` y en `/resetear-password`  🔴

**Evidencia:** `pageerror: Minified React error #425 / #422 / #418` en `/cuenta`,
`/cuenta/menores`, `/cuenta/reservas`, `/cuenta/reservas/[id]`, todo `/cuenta/*` del tutor y
`/resetear-password`. #418/#425 = el HTML del servidor no coincide con el primer render del cliente;
#422 = React tiró el árbol del servidor y re-renderizó todo en el cliente (parpadeo y trabajo doble).

**Qué hacer:**
1. Reproducí en desarrollo (`bun run dev`) para ver el mensaje completo, no minificado.
2. Causas típicas acá: leer la sesión de `localStorage`/cookie durante el render (el menú del shell
   de cuenta cambia según el rol), y formatear fechas con `toLocaleString` (el servidor corre en UTC
   y el navegador en `America/Argentina/Buenos_Aires`).
3. Arreglo: lo que depende del navegador se lee en `useEffect` o con `useSyncExternalStore` con un
   snapshot de servidor estable, y **toda fecha se formatea con zona horaria explícita**
   (`timeZone: "America/Argentina/Buenos_Aires"`) desde un único helper en `lib/`.
4. Verificación: cero `pageerror` en el recorrido de §6 del LEEME.

## B4 — La agenda del tutor no carga (usa el DNI como id)  🔴

**Evidencia:** en `/cuenta/horarios`, `GET /api/tutores/30224455/franjas` → **403**. El tutor Jorge
tiene 11 franjas publicadas y la grilla aparece vacía, con el mensaje "El listado de franjas está
pendiente en backend". `cuenta/horarios/page.tsx` usa `session.payload.sub` como `tutorId`, y hoy el
`sub` del JWT es el DNI.

**Qué hacer:** si **FASE3-03** (sub = UUID) ya está mergeada, solo verificá. Si no, pedí el id con
`GET /api/usuarios/me` (existe) en vez de leerlo del token. Y reemplazá el mensaje de desarrollador
por el estado real (ver B9).

## B5 — El menor no tiene cómo pedir una clase  🔴

**Evidencia:** en `/reservar` como menor aparece "Tu Adulto Responsable debe reservar por vos" y
nada más. El backend **tiene** el flujo de solicitudes (el menor propone, el AR aprueba en
`/cuenta/menores`), y `components/DynamicTimeSlotPicker.tsx` ya muestra "Enviar Solicitud de
Aprobación" cuando el rol es menor, pero **el picker no está conectado a `/reservar`**
(T-M4-13 a T-M4-15 abiertas). El flujo del Artículo II está roto de punta a punta en la UI.

**Qué hacer:** se resuelve en `04-descubrir-reservar-pagar.md` (integración del picker). Acá, como
mínimo inmediato: que el mensaje del menor tenga una acción ("Pedile a tu adulto responsable…"),
no un callejón sin salida.

## B6 — Enums y datos internos en pantalla

**Evidencia:** detalle de reserva del AR: "Motivo de cancelacion: **timeout_pago**". Perfil:
"Capacidad Estudiante: Activa". Perfil del tutor: "Sesion de Adulto".

**Qué hacer:** un único mapa de etiquetas humanas en `lib/etiquetas.ts` para cada enum que viaja
en JSON (estado de reserva, motivo de cancelación, estado de credencial, tipo de documento, estado
de denuncia/alerta…). Prohibido renderizar un enum crudo. Ejemplo: `timeout_pago` → "No se completó
el pago a tiempo".

## B7 — Tildes y ortografía

**Evidencia (no exhaustiva):** "mayor de 18 **anos**", "**Contrasena**", "**Cerrar sesion**",
"**Iniciar sesion**", "Ya **tenes** cuenta?", "**Calificacion**", "Motivo de **cancelacion**",
"**Sabado**", "**Sesion** de Adulto".

**Qué hacer:** barrido completo del frontend. Punto de partida:
`rg -n -i "\banos\b|contrasena|sesion\b|tenes\b|calificacion|cancelacion|sabado|verificacion|informacion|accion\b|codigo|numero\b" frontend/src`
(revisá cada match: algunos son identificadores de código, que no se tocan; solo el texto visible).
Todo texto visible va en castellano rioplatense con tildes correctas y **voseo** consistente.

## B8 — Sección de la landing invisible

**Evidencia:** "Pensado para que las familias confíen" aparece como un bloque vacío enorme (desktop
y mobile). `components/TarjetasSeguridad.tsx` oculta las tarjetas hasta que un
`IntersectionObserver` las marca visibles.

**Qué hacer:** el contenido es visible **por defecto**; la animación es una mejora progresiva
(respetando `prefers-reduced-motion`). Nunca contenido que solo aparece si un observer se dispara.

## B9 — Mensajes que le mienten al usuario o que son para desarrolladores

| Dónde | Hoy | Tiene que decir |
|---|---|---|
| `/recuperar-password` | "te vamos a enviar un enlace…", pero **no existe canal de envío** (AUD-008/014, P5) | Hasta que exista el email: "Por ahora la recuperación se hace con soporte: escribinos a …" o deshabilitar el flujo con esa explicación. **No prometer un email que no sale.** |
| `/cuenta/horarios` | "El listado de franjas está pendiente en backend" | El estado real (sus franjas) o un error humano. |
| `/admin` (todas) | "cada cola valida el permiso del lado del servidor" | Nada: es un detalle de implementación. |
| `/pagar` y `/reservar` sin parámetro | Error rojo + "Reintentar" (que no reintenta nada) | Redirigir a `/cuenta/reservas` o a `/buscar` con un mensaje neutro. |

## B10 — Navegación que se contradice

- En el shell de cuenta quedan **dos ítems activos a la vez** ("Perfil" siempre queda marcado).
  Solo el ítem de la ruta actual va activo.
- El panel de admin muestra todas las secciones sin importar el rol: un Admin de Moderación entra a
  "Pagos fallidos" y a "Salud" y le sale "No tenés permiso". **[backend]** Agregar
  `GET /api/admin/yo` → `{rol}` (gateado como el resto de `/api/admin/**`) y ocultar del menú lo que
  el rol no puede usar. El backend sigue validando igual: esto es UX, no seguridad.

## B11 — Requests que siempre fallan

- Detalle de una reserva **cancelada**: `GET /api/sesiones/por-reserva/{id}` → 404 en cada carga. No
  pedir la sesión cuando el estado de la reserva no puede tener una.
- `/cuenta/precio`: `GET /api/pagos/precio-referencia/Buenos%20Aires` → 404 si no hay referencia.
  **[backend]** Responder **204** (sin referencia) en vez de 404, o que el frontend lo trate como
  estado vacío esperado sin loguear error.

## B12 — El tutor verificado ve "Tu credencial está en revisión"

**Evidencia:** el tutor Jorge tiene en la base una credencial `APROBADO` **y** otra `PENDIENTE` más
nueva. `/cuenta` le muestra "Tu credencial está en revisión por el equipo de Tinku", como si no
estuviera verificado, porque el banner (`components/BannerCredencial.tsx`) mira solo la última
credencial (`GET /api/tutores/me/credencial` devuelve la más reciente). El tutor cree que no puede
recibir alumnos cuando sí puede.

**Qué hacer:** si tiene alguna aprobada, el mensaje es "Tu perfil está verificado. Tu nueva credencial
está en revisión." **[backend]** si hace falta, que el endpoint devuelva también si existe una
aprobada (o usar el `GET /api/tutores/me/estado-perfil` de `06-tutor.md`).

## B13 — La primera búsqueda tarda 34 segundos

**Evidencia (medido):** primera búsqueda después de levantar el stack: **33.7 s** con skeletons; la
segunda: 0.1 s. `matching-service` carga el modelo de embeddings en la primera request
(`_cargar_embedder`). **[backend]** Precargarlo en el arranque del servicio. Detalle en
`04-descubrir-reservar-pagar.md` §1.

## Criterios de aceptación

- B1 a B11 resueltos, un commit por ítem.
- Recorrido completo con el método de §6 del LEEME: **cero** `pageerror`, **cero** 4xx/5xx no
  esperados en `errores-consola-y-red.json` "después".
- Suite del backend verde (por B2, B10 y B11) y frontend: lint, typecheck y E2E verdes.
