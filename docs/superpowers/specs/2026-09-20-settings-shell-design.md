# Settings Shell — rediseño de `/cuenta` y `/admin`

Fecha: 2026-09-20
Autor: sesión de diseño con Claude (santy)
Estado: aprobado para plan + ejecución

## Problema

`/cuenta` y `/admin` son "hubs" multi-sección implementados como una sola
página cliente gigante con un switch de tabs en memoria (`useState<IdTab>`).
Dos problemas concretos, verificados en código:

1. **Bug real — tabs duplicadas.** `PanelTutor` en
   `frontend/src/app/cuenta/page.tsx` (líneas 75-149) renderiza un `<nav>`
   manual con botones (líneas 85-116) que repite las mismas 3 pestañas
   (Horarios/Materias/Precio) que el componente `<Tabs>` (línea 121) vuelve
   a renderizar debajo. Dos sistemas de navegación para lo mismo.
2. **Amontonamiento sin jerarquía.** Un usuario Tutor + Adulto Responsable ve,
   en una sola página, sin poder navegar por secciones: info de cuenta →
   tabs del tutor (duplicadas) → `PanelAdulto` completo (alta de menor,
   solicitudes pendientes, baja de menor — todo siempre montado, siempre
   haciendo fetch) → `EditarCuenta` (3 tarjetas más: Email, Contraseña,
   Capacidades). `/admin` tiene el mismo patrón de fondo (7 tabs planas en
   una sola página), sin el bug de duplicación pero con la misma falta de
   jerarquía, deep-linking y agrupación.

Pantallas de una sola tarea (`/buscar`, `/reservar`, `/pagar`, `/aula/[id]`,
`/login`, `/registro`, `/cuenta/seguridad`, `/cuenta/reservas`,
`/tutores/[id]`) están bien construidas (empty states, skeletons, manejo de
error) y quedan **fuera de alcance** — no se tocan.

## Objetivo

Llevar `/cuenta` y `/admin` al patrón de navegación de Ajustes de Apple
(macOS System Settings / iOS Settings), adaptado a Next.js App Router: una
lista de secciones agrupadas con navegación por **rutas reales**, un panel
de contenido que muestra una sección a la vez, sidebar persistente en
desktop y lista-que-navega en mobile.

## Arquitectura

### Componente nuevo: `SettingsShell`

`frontend/src/components/settings/SettingsShell.tsx` — recibe una lista de
grupos de navegación (`{ titulo, items: { href, label }[] }[]`) y renderiza:

- **Desktop (`lg:` — mismo breakpoint que ya usa `PanelTutor` hoy)**: sidebar
  fija a la izquierda con los grupos y sus items (usando `usePathname()` para
  resaltar el activo), panel de contenido (`children`) a la derecha.
- **Mobile (`<lg`)**: en la ruta raíz del shell (`/cuenta` o `/admin`) se
  muestra la lista agrupada de secciones a ancho completo, sin panel de
  contenido. Al navegar a una sub-ruta se muestra solo esa sección con un
  link "← Volver" arriba. No hay estado propio de "abierto/cerrado": es
  el pathname el que decide qué se ve, así el botón atrás del navegador
  funciona nativo, sin JS extra.

Es el mismo componente para `/cuenta` y `/admin`, configurado con distinta
lista de grupos en cada `layout.tsx`.

### `/cuenta` — mapa de secciones

Grupos (respetando qué hoy es condicional a `payload.tipo`/`payload.cap_ar`):

- **Cuenta** (siempre)
  - `/cuenta` — Perfil: info de solo lectura (DNI, tipo de cuenta) +
    formulario de Capacidades (Estudiante/Adulto Responsable), migrado tal
    cual desde el tercer `<Tarjeta>` de `EditarCuenta`.
  - `/cuenta/acceso` — Email + Contraseña, migrados tal cual desde las
    primeras dos `<Tarjeta>` de `EditarCuenta`. Se llama "acceso" y no
    "seguridad" para no colisionar con la sección de abajo, que ya significa
    otra cosa en el dominio.
- **Tutor** (solo si `payload.tipo === "TUTOR"`)
  - `/cuenta/horarios` — `<TabHorarios tutorId={...} />`, tal cual.
  - `/cuenta/materias` — `<TabMaterias />`, tal cual.
  - `/cuenta/precio` — `<TabPrecio />`, tal cual.
- **Adulto responsable** (solo si `payload.cap_ar === true`)
  - `/cuenta/menores` — todo el contenido actual de `PanelAdulto` (alta de
    menor, solicitudes pendientes, baja de menor), tal cual.
- **General** (siempre)
  - `/cuenta/reservas` — ya existe, se adopta al nav sin tocar su lógica.
  - `/cuenta/seguridad` — ya existe (denuncias y alertas recibidas), se
    adopta al nav sin tocar su lógica ni su URL.

"Buscar tutores" deja de aparecer dentro del nav de Ajustes (no es un
ajuste); sigue disponible donde ya está, en `Cabecera`.

`BannerCredencial` (hoy dentro de `PanelTutor`) se muestra en la sección
`/cuenta` (Perfil) cuando `payload.tipo === "TUTOR"`, no en cada sub-sección.

### `/admin` — mapa de secciones

Incluyendo `admin/layout.tsx` con el guard de rol que hoy hace cada endpoint
del lado del servidor (se mantiene igual, cada cola sigue validando el
permiso).

- **Seguridad**: `/admin/alertas` (`ColaAlertas`), `/admin/denuncias`
  (`ColaDenuncias`), `/admin/credenciales` (`ColaCredenciales`).
- **Financiero**: `/admin/pagos` (`ColaPagosFallidos`), `/admin/precios`
  (`PreciosRegionales`).
- **Soporte**: `/admin/tickets` (`TicketsSoporte`).
- **Sistema**: `/admin/salud` (el actual `SaludTab`, con
  `AdminInfrastructurePanel`).

Mismo orden que ya impone el comentario "Artículo II" en el código actual
(seguridad del menor primero). `/admin` raíz redirige a `/admin/alertas` en
desktop (primera sección por defecto) y muestra la lista agrupada en mobile.

## Archivos afectados

**Nuevos:**
- `frontend/src/components/settings/SettingsShell.tsx`
- `frontend/src/app/cuenta/layout.tsx`
- `frontend/src/app/cuenta/acceso/page.tsx`
- `frontend/src/app/cuenta/horarios/page.tsx`
- `frontend/src/app/cuenta/materias/page.tsx`
- `frontend/src/app/cuenta/precio/page.tsx`
- `frontend/src/app/cuenta/menores/page.tsx`
- `frontend/src/app/admin/layout.tsx`
- `frontend/src/app/admin/alertas/page.tsx`
- `frontend/src/app/admin/denuncias/page.tsx`
- `frontend/src/app/admin/credenciales/page.tsx`
- `frontend/src/app/admin/pagos/page.tsx`
- `frontend/src/app/admin/precios/page.tsx`
- `frontend/src/app/admin/tickets/page.tsx`
- `frontend/src/app/admin/salud/page.tsx`

**Modificados (reescritos, no borrados — misma lógica de negocio, nueva
ubicación):**
- `frontend/src/app/cuenta/page.tsx` — queda solo con "Perfil" (info +
  capacidades). `PanelTutor`, `PanelAdulto` se eliminan de acá (se mudan).
- `frontend/src/app/admin/page.tsx` — se elimina (reemplazado por
  `layout.tsx` + páginas por sección).
- `frontend/src/components/EditarCuenta.tsx` — se separa en la lógica de
  Email+Password (va a `/cuenta/acceso`) y Capacidades (va a `/cuenta`).

**Sin cambios:** `TabHorarios`, `TabMaterias`, `TabPrecio`, `ColaAlertas`,
`ColaDenuncias`, `ColaCredenciales`, `ColaPagosFallidos`,
`PreciosRegionales`, `TicketsSoporte`, `AdminInfrastructurePanel`,
`BannerCredencial`, `ui/Tabs.tsx` (se deja de usar acá, sigue disponible
para tabs de contenido genuinas en otras pantallas), `Cabecera.tsx`.

## Tests (Playwright) — impacto y plan

Suite actual acoplada a `/cuenta` como página única:
`tests/cuenta/cuenta.spec.ts`, `editar-cuenta.spec.ts`, `capacidades.spec.ts`,
`credencial-tutor.spec.ts`, y sus Page Objects (`cuenta-page.ts`,
`seguridad-page.ts`). Y de `/admin`: `admin.spec.ts`, `tickets.spec.ts`.

`CuentaPage.goto()` hoy solo visita `/cuenta`; sus locators
(`campoEmail`, `botonCambiarPassword`, `selectMenorBaja`, etc.) asumen que
todo está en esa misma página. Con el nuevo mapa dejan de estarlo.

**Plan:** actualizar cada Page Object para exponer un `goto()` por sección
(o parámetro de ruta), y cada spec para navegar a la sección correspondiente
antes de interactuar (ej.: ir a `/cuenta/acceso` antes de tocar
`campoEmail`/`botonCambiarPassword`; ir a `/cuenta/menores` antes de
`selectMenorBaja`/`botonDarDeBaja`). Mismo criterio para `admin.spec.ts`/
`tickets.spec.ts` con las nuevas rutas `/admin/*`. No se reduce cobertura:
mismo comportamiento verificado, distinta URL de entrada.

## Criterios de aceptación

1. Ninguna sección se renderiza duplicada en el DOM.
2. Cada sección tiene su propia URL, navegable con atrás/adelante del
   navegador y compartible por link directo.
3. Las secciones condicionales a rol (Tutor / Adulto Responsable) solo
   aparecen en el nav cuando corresponde — misma condición que hoy
   (`payload.tipo`, `payload.cap_ar`), sin relajar ni endurecer el guard.
4. Mobile (`<lg`): la raíz del shell muestra la lista agrupada; entrar a una
   sección muestra solo esa sección con "← Volver".
5. Desktop (`≥lg`): sidebar persistente + panel de contenido, sección activa
   resaltada según `usePathname()`.
6. Ninguna lógica de negocio (fetch, validaciones, mutaciones) cambia de
   comportamiento — es una migración de composición/routing, no una
   reescritura funcional.
7. `bun run lint` y `npx tsc --noEmit` limpios.
8. Suite Playwright de `/cuenta` y `/admin` actualizada y verde contra las
   nuevas rutas.

## Fuera de alcance

`/buscar`, `/reservar`, `/pagar`, `/aula/[id]`, `/login`, `/registro`,
`/registro/tutor`, `/tutores/[id]`, `/recuperar-password`,
`/resetear-password`. Ningún cambio de backend. Ningún cambio visual a los
átomos del kit `ui/` (Tarjeta, Boton, Campo, etc.) — solo cambia cómo se
componen y navegan las páginas hub.
