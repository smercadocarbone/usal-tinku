# UX-01 — Sistema visual nuevo de Tinku

**Branch:** `ux/sistema-visual` · **Precondición:** UX-02 mergeada.
**Alcance:** tokens, tipografía, componentes base y el AppShell. **No** se rediseñan pantallas acá:
se construyen las piezas con las que después se rehacen todas.

## 1. Por qué un sistema nuevo

Hoy (`frontend/src/app/globals.css`) el "sistema" es la paleta nativa de Tailwind (slate + teal),
la fuente del sistema operativo y 12 componentes en `components/ui/` usados de forma despareja. El
resultado se ve en las capturas: pantallas correctas pero genéricas, frías y sin jerarquía, sin
personalidad para una marca que les pide a las familias que confíen su hijo a un desconocido.

**Principios de la marca (definen cada decisión de abajo):**
1. **Confianza tranquila.** Seguro sin ser frío ni corporativo. Nada de rojo alarmista salvo peligro real.
2. **Cercanía argentina.** Voseo, calidez, cero jerga técnica.
3. **Claridad para dos públicos.** Adultos apurados en el celular y chicos desde los 6 años.
4. **Menos, pero mejor.** Una acción principal por pantalla.

## 2. Tokens (Tailwind 4, `@theme` en `globals.css`)

Tokens **semánticos** con nombre propio. Las pantallas usan los semánticos, nunca un color crudo de
la escala: así el próximo cambio de marca es tocar un archivo.

### Color

| Token | Uso | Valor propuesto |
|---|---|---|
| `--color-marca-50…900` | Marca: un verde azulado más profundo y cálido que el teal actual | 50 `#effaf6` · 100 `#d8f2e8` · 200 `#b3e4d3` · 300 `#7fcfb6` · 400 `#48b394` · 500 `#26967a` · 600 `#187a63` · **700 `#146251`** (primario) · 800 `#134f43` · 900 `#113f37` |
| `--color-acento-50…700` | Acento cálido para destacar lo positivo (precio, "nuevo", pasos completos). **Nunca** para errores. `acento-500` solo como **relleno** con texto `tinta` encima (7.6:1); como **texto** sobre blanco, solo `acento-700` (4.6:1). `acento-500` como texto sobre blanco da 2.1:1: prohibido. | 50 `#fff7ed` · 100 `#ffeccc` · 300 `#ffc26b` · **500 `#f59e2b`** · 700 `#b45f06` |
| `--color-tinta` / `--color-tinta-suave` / `--color-tinta-tenue` | Texto principal, secundario, terciario | `#14231f` (15.3:1) / `#475a55` (6.9:1) / `#5f716c` (5.2:1 sobre blanco, 4.8:1 sobre fondo) |
| `--color-superficie` / `--color-superficie-alta` / `--color-fondo` | Tarjetas, capas elevadas, fondo de página | `#ffffff` / `#ffffff` / `#f6f8f7` |
| `--color-borde` / `--color-borde-fuerte` / `--color-borde-control` | Separadores decorativos / bordes de tarjeta / **bordes de inputs y controles** (WCAG 1.4.11 exige 3:1 para el límite de un control) | `#e3e9e7` / `#c9d3d0` / `#7f8f8a` (3.4:1) |
| `--color-exito` / `--color-aviso` / `--color-peligro` / `--color-info` | Estados (cada uno con su `-suave` de fondo) | `#15803d` / `#b45309` / `#b91c1c` / `#1d4ed8` |

Los valores de arriba ya se verificaron con la fórmula de luminancia relativa de WCAG 2.1 (ratios
entre paréntesis). **Si cambiás un valor, re-verificalo:** texto ≥ **4.5:1** (3:1 solo en texto
≥ 18.66 px bold o ≥ 24 px), límites de controles ≥ **3:1**. Si un valor no cumple, se ajusta el
valor, nunca la regla.

### Tipografía

- **Fuente:** `Plus Jakarta Sans` (títulos y texto) vía `next/font/google`, con `display: swap` y
  subset `latin` + `latin-ext` (ñ, tildes). Es humana y redondeada sin ser infantil, y tiene muy
  buena legibilidad en tamaños chicos. `next/font` la descarga **en el build** y la sirve desde el
  propio dominio: no es una dependencia npm nueva y no hace requests a Google en runtime.
  **Ojo:** `next/font/google` necesita internet **durante el build** (incluido el build de la imagen
  Docker). Si el build tiene que funcionar sin red, usá `next/font/local` con los `.woff2` de la
  fuente commiteados en `frontend/src/app/fonts/` (licencia OFL: se pueden redistribuir).
- **Escala** (mobile → desktop): `display` 36→56 · `h1` 28→40 · `h2` 22→28 · `h3` 18→20 ·
  `cuerpo` 16 · `chico` 14 · `mini` 12. Interlineado 1.5 en cuerpo y 1.15 en títulos.
- **Números** (precios, horarios): `font-variant-numeric: tabular-nums`.

### Forma, espacio y elevación

- **Radios:** `--radius-control: 12px` (botones, inputs) · `--radius-tarjeta: 20px` ·
  `--radius-pastilla: 999px` (badges, chips).
- **Espaciado:** escala de 4 px de Tailwind. Padding de tarjeta 20 (mobile) / 24 (desktop). Ancho
  máximo de contenido 1120 px; de formularios, 480 px.
- **Elevación, 3 niveles:** `plano` (solo borde) · `elevado` (tarjetas, sombra suave y difusa) ·
  `flotante` (menús, modales, toasts).
- **Movimiento:** 150–250 ms, `ease-out`. Todo bajo `motion-safe:`. Nada que oculte contenido
  hasta que termine una animación (ver UX-02 B8).

## 3. Componentes base (`components/ui/`)

Se rehacen los existentes y se suman los que faltan. Cada uno con **todos sus estados** (reposo,
hover, foco visible, activo, deshabilitado, cargando, error), accesible por teclado y con
`displayName`/props tipadas. Documentá cada uno con un ejemplo en una página interna
`/dev/componentes`, visible **solo** en desarrollo (`NODE_ENV !== "production"`).

| Componente | Estado | Notas clave |
|---|---|---|
| `Boton` | rehacer | Variantes: `primario`, `secundario`, `fantasma`, `peligro`. Tamaños `sm`/`md`/`lg`. Prop `cargando` (spinner + texto, bloquea doble click). Alto mínimo 44 px en mobile. |
| `Campo` | rehacer | Label siempre visible (nunca solo placeholder), texto de ayuda, error bajo el campo con `aria-describedby`, ícono opcional. Variante `password` con botón "mostrar". Variante `dni` con `inputMode="numeric"`, máscara `00.000.000`. |
| `SubidaArchivo` | **nuevo** | Reemplaza el `<input type="file">` nativo (hoy dice "Choose File" en inglés). Zona para arrastrar o tocar, vista previa (imagen) o nombre y peso (PDF), formatos y tamaño máximo visibles **antes** de elegir, error humano si no cumple. |
| `Selector` | nuevo | `<select>` estilizado accesible. |
| `Tarjeta` | rehacer | Variantes `plana`, `elevada`, `interactiva` (toda la tarjeta clickeable, con foco). |
| `Insignia` / `EstadoReserva` | rehacer | Pastilla de color **+ ícono + texto** (nunca solo color). Mapa único estado → tono en `lib/etiquetas.ts` (ver UX-02 B6). |
| `Alerta` | rehacer | `info`, `exito`, `aviso`, `peligro`; con título, acción opcional y cierre. |
| `EstadoVacio` | rehacer | Ilustración o ícono, título, explicación y **una acción**. |
| `Skeleton` | mantener | Formas por tipo de contenido (lista, tarjeta, perfil). |
| `ModalConfirmacion` | **nuevo** | Para acciones destructivas o irreversibles (rechazar credencial, dar de baja un menor, cancelar reserva). Foco atrapado, `Escape` cierra, texto del botón = la acción ("Dar de baja a Sofía"), nunca "Aceptar". |
| `Toast` | **nuevo** | Confirmación no bloqueante ("Guardado"), `aria-live="polite"`. Reemplaza a `IndicadorGuardado`. |
| `Avatar` | **nuevo** | Foto o iniciales con color derivado del id. |
| `Pasos` (stepper) | **nuevo** | Para registros y reserva. Paso actual, completos y pendientes; en mobile, "Paso 2 de 4". |
| `Precio` | **nuevo** | Formato `$ 15.000` con `Intl.NumberFormat('es-AR', {style:'currency', currency:'ARS'})`, y variante "por hora". |
| `Estrellas` | **nuevo** | Calificación de lectura y de entrada (accesible como radio group). |
| `FechaHora` | **nuevo** | Formatea siempre con `America/Argentina/Buenos_Aires` (UX-02 B3). Formato corto `mar 24 sep · 18:00`, largo `martes 24 de septiembre, 18:00 a 19:00`. |
| `Tabs`, `Chip` | rehacer | Con el sistema nuevo. |

## 4. AppShell y navegación

Hoy cada zona tiene su cabecera (la landing no tiene ninguna, `/reservar` y `/pagar` tampoco, y en
mobile el botón "Cerrar sesion" ocupa media cabecera).

- **Una sola `Cabecera`** para toda la app, con tres variantes:
  - **pública** (landing, login, registro): logo → `/`, "Buscar tutores", "Ingresar" y "Crear cuenta".
  - **usuario**: logo, navegación principal **según rol** (ver tabla), avatar con menú (Mi cuenta,
    Cerrar sesión). Nunca un botón de "Cerrar sesión" suelto de primer nivel.
  - **admin**: igual que usuario, más el acceso al panel si el usuario es admin.
- **Mobile:** navegación inferior fija (bottom nav) con 3–4 destinos según rol, íconos + texto.
  La cabecera mobile queda con logo y avatar.
- **Shell de cuenta y de admin:** en desktop, menú lateral; en mobile, el patrón actual de
  menú → detalle con "← Volver" está bien y **se conserva**, pero la raíz no marca un ítem como
  activo si no está abierto (UX-02 B10).

| Rol | Navegación principal |
|---|---|
| Estudiante adulto | Buscar · Mis clases · Mi cuenta |
| Adulto Responsable | Buscar · Mis clases · Mis chicos · Mi cuenta |
| Menor | Buscar · Mis clases (sus solicitudes y clases) · Mi cuenta |
| Tutor | Mi agenda · Mis clases · Mi perfil · Mi cuenta |
| Admin | Panel (con contadores) · Mi cuenta |

## 5. Guía de voz y copy (obligatoria en todas las pantallas)

- **Voseo** siempre ("elegí", "tenés", "reservá"). Castellano con **tildes correctas**.
- **Humano, no técnico:** "Tu clase con Jorge" y no "Reserva #1499". Prohibidos los enums, los ids y
  los mensajes de desarrollador (UX-02 B6/B9).
- **Errores:** qué pasó + qué hacer ("No pudimos cargar tus clases. Revisá tu conexión y probá de
  nuevo."). Nunca "Error 403". Nunca culpar ("No se presentaron" → "La clase no se realizó").
- **Botones con verbo y objeto:** "Reservar clase", "Aprobar credencial". Nunca "Aceptar"/"OK".
- **Fechas y horas** siempre en hora argentina y con el formato de `FechaHora`.
- **Datos sensibles:** el DNI se muestra enmascarado (`••.•••.233`) salvo donde haga falta completo.
  Nada de datos de menores en URLs ni en títulos de pestaña.
- **Para menores:** frases cortas, sin ironía, sin urgencias falsas.

## 6. Criterios de aceptación

- Tokens en `globals.css` y fuente cargada con `next/font`. Sin colores crudos de la escala de
  Tailwind en componentes nuevos (`rg -n "(bg|text|border)-(slate|teal)-[0-9]" frontend/src/components/ui`
  → cero resultados).
- Todos los componentes de §3 en `components/ui/`, exportados desde `index.ts`, con su ejemplo en
  `/dev/componentes` (capturado en `capturas-despues/`).
- `Cabecera` única y bottom nav mobile funcionando para los 5 roles.
- `axe` sin violaciones en `/dev/componentes`. Lint, typecheck y E2E verdes.
- Esta spec **no** cambia pantallas todavía: las pantallas existentes pueden seguir usando los
  componentes viejos hasta su spec (mantené los nombres o dejá alias temporales).
