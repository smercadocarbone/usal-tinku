# R-G — Frontend: móvil, accesibilidad, títulos y cabeceras

**Tarea:** T-REM-G · **Rama:** `fix/rem-g-front` · **Depende de:** chunk B (B-10, `rolAdmin` en `/me`) para G-9 · **Decisiones:** D-8 (PWA), D-9 (CSP)
Fuente: `hallazgos_parciales/T7.md`, `T6.md`, `T2.md` (H5–H7). Capturas de referencia en
`docs/operacion/hallazgos_parciales/` (T7 las dejó en el scratchpad; si no están, regenerarlas con el criterio de cada ítem).

Todos los tests de móvil usan Playwright con `viewport: { width: 360, height: 640 }`. Criterio común de "sin scroll horizontal":
`document.documentElement.scrollWidth <= window.innerWidth`.

## G-1 `/cuenta/horarios` ensancha la página en móvil (T7-M1)

`frontend/src/components/tutor/TabHorarios.tsx` + `app/cuenta/horarios/page.tsx`: la tira de pestañas ("Disponibilidad semanal fija /
puntual", `overflow-x-auto w-fit`) y la grilla (`min-w-[42rem]`) hacen `scrollWidth` 446 contra 360, y la grilla solo muestra Lun/Mar/Mié.
Arreglo: la grilla vive dentro de un contenedor `overflow-x-auto max-w-full` (el scroll horizontal queda **dentro** del
componente, con `min-w-0` en los padres flex/grid); las pestañas pasan a `flex-wrap` o a un `select` en móvil; los 7 días son
alcanzables por scroll del contenedor. **Test (RED):** con el seed mockeado (105 franjas), en 360×640 `scrollWidth <= innerWidth`,
y los 7 encabezados de día existen en el DOM.

## G-2 `/cuenta/materias`: los chips se salen del card (T7-M2)

`components/tutor/TabMaterias.tsx` / `app/cuenta/materias/page.tsx`: los chips de "Tus temas" (ej. "Números Reales y radicales
(Matemática, 4°) ×") no cortan línea. Arreglo: `max-w-full whitespace-normal break-words` en el chip y `flex-wrap` en el
contenedor. **Test (RED):** tema con nombre largo mockeado → `scrollWidth <= innerWidth` y el chip queda dentro del card
(`boundingBox().right <= card.boundingBox().right`).

## G-3 Un título de pestaña por pantalla (T7-5)

Hoy solo `/registro`, `/registro/tutor` y `/recuperar-password` tienen título propio; el resto usa "Tinku — Tutorías en línea".
Las páginas son `"use client"`, así que `metadata` no se puede exportar desde ellas: agregar un `layout.tsx` de servidor por
segmento que exporte `metadata`, y en `app/layout.tsx` un `title: { template: "%s · Tinku", default: "Tinku — Tutorías en línea" }`.
Títulos: `/login` "Ingresar", `/buscar` "Buscar tutores", `/reservar` "Reservar una clase", `/pagar` "Pagar", `/resetear-password`
"Nueva contraseña", `/cuenta/reservas` "Mis clases", `/cuenta/perfil` "Mi perfil", `/cuenta/acceso` "Seguridad y acceso",
`/cuenta/seguridad` "Casos y reportes", `/cuenta/horarios` "Mis horarios", `/cuenta/materias` "Mis materias",
`/cuenta/precio` "Mi precio", `/cuenta/cobros` "Mis cobros", `/cuenta/menores` "Mis chicos", `/aula/[id]` "Aula",
`/admin/*` "Administración", la 404 "Página no encontrada". **Test (RED):** una tabla de ruta → `document.title` con
`page.goto` y `expect(page).toHaveTitle`, con la API mockeada.

## G-4 Objetivos táctiles de al menos 24 px (T7-8, WCAG 2.2 criterio 2.5.8)

Enlaces de 18–23 px de alto: "Volver al inicio" (95×20), "¿Olvidaste tu contraseña?" (177×18), "Creala en 3 minutos", "Ingresá",
"Ver clases y tutores", "Buscar un tutor para Benjamín", "Clase con X" en Cobros (21 px), y el switch "Ofrecer el paquete…" en
`/cuenta/precio` (13×20). Arreglo: clase utilitaria `.enlace-tactil { display:inline-flex; align-items:center; min-height:24px }`
(44 px en pantallas ≤ 640 px) aplicada a esos enlaces; el switch con un área de toque de 44×24 (padding, no cambia el dibujo).
**Test (RED):** Playwright recorre las pantallas de T7 y exige que todo `a`/`button`/`[role=switch]` visible tenga
`boundingBox().height >= 24` y `width >= 24`, salvo enlaces dentro de un párrafo (excepción de la propia norma).

## G-5 Contraste y foco (T7-9, T7-10)

1. Etiquetas de hora de la grilla de horarios: `#7F8F8A` sobre blanco da 3,39:1 a 12 px. Usar un token con ≥ 4,5:1
   (calculalo: p. ej. `#5B6B66`; verificá con la fórmula WCAG o con axe). Reemplazar en el componente, no en el token global,
   salvo que otros usos del mismo token también fallen.
2. Foco visible: regla global en `app/globals.css`
   `:focus-visible { outline: 2px solid var(--color-foco); outline-offset: 2px; }` y buscar con
   `rg "outline-none|focus:outline-none" frontend/src` los que lo anulan sin poner un anillo equivalente (`focus-visible:ring-*`).
   Casos citados: input `hero-buscar` de la home, `buscar-texto`, `precio`, el `DIV` enfocable de "Todavía no tenés clases".
   Un `div` enfocable que no es interactivo no debería tener `tabindex`: quitarlo.
3. Orden de foco en `/cuenta/perfil` y `/cuenta/seguridad` con el banner de Términos: que el banner esté **después** del contenido
   en el DOM o que su botón sea el primero lógico (decidí por lo que lea mejor con lector de pantalla; documentalo).
**Tests (RED):** `@axe-core/playwright` sin violaciones `color-contrast` ni `focus-order-semantics` en `/cuenta/horarios`,
`/cuenta/perfil`, `/cuenta/seguridad`, `/buscar` y `/`; un test que recorre con `Tab` esas pantallas y comprueba que el elemento
activo tiene `outline-style != none` o `box-shadow != none`.

## G-6 Un solo `h1` (T7-12)

`/cuenta/seguridad` tiene dos (`"Mi cuenta"` y `"Denuncias y alertas de seguridad"`). El segundo pasa a `h2`.
**Test (RED):** Playwright cuenta `h1` por pantalla de T7 y exige 1.

## G-7 Textos aclaratorios de Mis cobros y de Precio (T2-H5, H6, H7)

1. **Mis cobros** (`app/cuenta/cobros`): con totales en $0 y filas "Cobrado $X" simuladas, mostrar
   "Los pagos simulados (pruebas) no suman a los totales." encima de los totales. Y debajo del neto de cada clase:
   "Mercado Pago descuenta su comisión aparte (≈6,04 %)." para que no contradiga el neto de la pestaña Precio.
2. **Precio** (`app/cuenta/precio`): si el valor es menor al piso o inválido, **ocultar** "Así lo ven las familias" y
   "Te quedan aprox." y mostrar solo el mensaje del piso. **Test (RED):** Playwright con 6139 → esos dos bloques no existen;
   con 6140 → existen.

## G-8 `Content-Security-Policy` en el front (T6) — Dec D-9

`frontend/next.config.js` tiene un comentario explícito de por qué no hay CSP ("una CSP mal escrita rompe en silencio").
Entonces: **primero `Content-Security-Policy-Report-Only`**, una semana en producción, y recién después se aplica.
Cabecera (ajustala al listar los orígenes reales con `rg -o "https?://[a-z0-9.-]+" frontend/src | sort -u`):
```
default-src 'self';
script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval';   # 'wasm-unsafe-eval': TF.js usa WebAssembly (ADR-M3-01)
style-src 'self' 'unsafe-inline';
img-src 'self' data: blob: https://api.tinku.site;
media-src 'self' blob:;
font-src 'self';
connect-src 'self' https://api.tinku.site wss://*.livekit.cloud https://*.livekit.cloud;
worker-src 'self' blob:;
frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://*.mercadopago.com https://*.mercadopago.com.ar;
report-uri /api/csp-report;
```
- El endpoint `/api/csp-report` **no existe**: no lo agregues. Usá solo `Report-Only` con la consola del navegador por ahora, y
  agregá el reporte en un chunk aparte si el dueño lo pide.
- Si MercadoPago redirige a otro dominio (checkout), agregarlo a `form-action` y `connect-src` según lo que muestre la consola.
**Test (RED→GREEN):** Playwright escucha `securitypolicyviolation` (`page.addInitScript`) y recorre `/`, `/login`, `/buscar`,
`/tutores/{id}`, `/reservar`, `/aula/{id}` (con el modelo falso de dev y LiveKit mockeado): **0 violaciones**. Mientras haya
violaciones, no se pasa de `Report-Only` a `Content-Security-Policy`.
**HSTS de la API:** no va acá; es la acción **O-9** (Cloudflare).

## G-9 Dejar de pedir `/api/admin/yo` en cada pantalla (T1-7, T2-H12)

`frontend/src/lib/usePerfil.ts` (línea ~66) llama a `/api/admin/yo` y recibe 403 para todo no-admin. Con B-10, `/api/usuarios/me`
trae `rolAdmin`; `usePerfil` lo toma de ahí y **no** llama a `/api/admin/yo`. **Test (RED):** Playwright con sesión de Estudiante:
ninguna request a `/api/admin/yo` y la consola sin errores (`page.on("console")` filtrando `error`).

## G-10 PWA (T7-7) — Dec D-8, **opcional**

Solo si el dueño lo pide. Manifest ya válido; falta service worker, íconos 192/512/maskable, `favicon.ico`, `apple-icon.png`.
Alcance mínimo: íconos PNG generados desde `app/icon.svg` (renderizalos con Chromium de Playwright, no agregues dependencias),
`app/apple-icon.png`, `favicon.ico`, y `orientation: "any"` en `app/manifest.ts`. **Service worker: no** en el piloto
(riesgo de cachear respuestas con sesión o datos de menores; si se hace, solo una página `/offline` estática y sin caché de `/api`).

## Verificación del chunk

- `npm run lint && npm run build && npx playwright test`.
- Repetir el recorrido de T7 en 360×640 sobre producción (o el entorno de pruebas): 0 hallazgos medios.
- Marcar cerrados: T7-M1, T7-M2, T7-5, T7-8, T7-9, T7-10, T7-12, T2-H5, T2-H6, T2-H7, T1-7, T2-H12 y el de CSP; tildar T-REM-G.

## NO tocar

- La lógica de negocio ni los endpoints (salvo lo ya hecho en B-10).
- El service worker (ver G-10).
