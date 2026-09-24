# Rediseño UX/UI de Tinku — specs para ejecutar con opencode

> **Leé este archivo completo antes de abrir cualquier spec de UX.** Rigen también `AGENTS.md`
> y, para cualquier cambio de backend que una spec pida, el protocolo de
> `docs/superpowers/specs/remediacion/00-LEEME-opencode.md` (TDD, suite, guardrails).

## 1. De dónde sale esto

Auditoría hecha el 2026-09-23 sobre el stack local corriendo (`main` con FASE 1 mergeada),
recorriendo **todas las pantallas** con **6 roles** (anónimo, estudiante adulto, Adulto
Responsable, menor, tutor, admin de moderación) y **2 viewports** (desktop 1440 px, mobile 390 px).

- **Evidencia "antes":** `capturas-antes/` → `<rol>__<ruta>__<viewport>.png` (80 capturas).
- **Errores de consola y de red por pantalla:** `capturas-antes/errores-consola-y-red.json`.

**Decisión del usuario:** rediseño visual **completo** (no solo retoques), ejecutado por opencode
a partir de estas specs.

## 2. Diagnóstico en una línea por área

| Área | Diagnóstico |
|---|---|
| **Funcional** | Hay bugs que ningún rediseño tapa: hidratación de React rota en todo `/cuenta`, la agenda del tutor no carga (usa el DNI como id), el flujo de solicitud del menor no existe en la UI, enums crudos en pantalla. → `02-bugs-y-deuda-ux.md` **primero**. |
| **Confianza** | Para una plataforma con menores y pagos, las pantallas que generan confianza son las más pobres: el perfil del tutor muestra solo el nombre, no se ve el precio antes de pagar, "Denunciar" compite con "Reservar". |
| **Embudo** | Landing sin header ni login; promete buscar sin registrarse y no se puede; reservar mezcla franjas pasadas con segundos y no muestra precio ni duración. |
| **Consistencia** | Cada pantalla resuelve distinto lo mismo (registro adulto en 4 pasos vs. tutor en un formulario plano; cabeceras distintas; inputs de archivo nativos en inglés). |
| **Copy** | Tildes faltantes ("anos", "Contrasena", "sesion"), jerga interna ("Capacidad Estudiante", "Sesion de Adulto"), mensajes de desarrollador ("pendiente en backend"), enums (`timeout_pago`). |
| **Estados** | Vacíos sin guía, errores sin salida ("Reintentar" que no reintenta nada), secciones enteras invisibles por animaciones que no se disparan. |

## 3. Orden de ejecución

| # | Spec | Qué | Depende de |
|---|---|---|---|
| 1 | `02-bugs-y-deuda-ux.md` | Bugs funcionales y de copy. **Sin rediseño todavía.** | — |
| 2 | `01-sistema-visual.md` | Tokens, tipografía, componentes base y AppShell. | 1 |
| 3 | `03-publico.md` | Landing, login, registros, recuperar/resetear. | 2, **U2** |
| 4 | `04-descubrir-reservar-pagar.md` | Buscar, perfil del tutor, reservar, pagar. | 2, **FASE2-01**, **U1** |
| 5 | `05-cuenta.md` | Cuenta del estudiante, del Adulto Responsable y del menor. | 2 |
| 6 | `06-tutor.md` | Onboarding, horarios, materias, precio. | 2, **FASE2-01**, **FASE3-03**, **U1** |
| 7 | `07-aula.md` | Lobby y sala. | 2 |
| 8 | `08-admin.md` | Panel de administración. | 2 |

Una spec = un branch `ux/<slug>` desde `main` = PR revisable. Los componentes del sistema visual
van primero y **solos**, para que las pantallas se construyan con ellos y no al revés.

## 4. Reglas para todas las specs de UX

1. **No se rompe ningún contrato del backend.** Si una pantalla necesita un dato que el backend no
   manda, la spec lo pide explícitamente como **cambio de backend** (sección "Backend" de cada
   spec) y ese cambio sigue el protocolo de remediación: test primero, suite completa.
2. **Tests del frontend:** a diferencia de las specs de remediación (regla A9), acá **sí** se
   actualizan los E2E de `frontend/tests/` cuando cambia la UI (selectores, textos, flujos). Nunca
   para ocultar un bug: si un E2E falla por un comportamiento que cambió sin querer, el bug es tuyo.
3. **Verificación visual obligatoria:** al terminar cada spec, re-capturá las pantallas tocadas con
   el mismo método (ver §6) en desktop y mobile, y guardalas en `capturas-despues/`. Una spec de UX
   sin capturas "después" **no está terminada**.
4. **Cada pantalla tiene sus 4 estados diseñados:** cargando (skeleton, no spinner suelto), vacío
   (qué es, por qué está vacío, qué hacer), error (qué pasó en lenguaje humano, qué puede hacer el
   usuario) y éxito/contenido.
5. **Accesibilidad mínima (WCAG 2.1 AA):** contraste ≥ 4.5:1 en texto, foco visible, todo operable
   con teclado, `label` en cada input, `aria-live` en mensajes de estado, targets táctiles ≥ 44 px
   en mobile. Los E2E ya corren `@axe-core/playwright`: no pueden sumar violaciones.
6. **Mobile first:** la mayoría de las familias entra desde el celular. Diseñá a 390 px y después
   ampliá.
7. **Nada de lo prohibido por el proyecto:** sin tracking, sin analytics de terceros, sin datos de
   menores en URLs, sin mostrar el DNI completo si no hace falta.
8. **Validación:** `cd frontend && bun run lint && npx tsc --noEmit -p . && bun run test:e2e`.

## 5. Decisiones PENDIENTES de UX (frenan la parte que las necesita)

| ID | Pregunta | Recomendación | Respuesta del usuario |
|---|---|---|---|
| U1 | ¿El tutor puede cargar **bio** y **foto** en su perfil público? No está en ningún Spec (alcance nuevo, Artículo VI). Sin eso, el perfil del tutor sigue vacío y es la pantalla que más confianza tiene que generar. | **Sí, las dos, con límites:** bio de hasta 500 caracteres moderable desde el panel; foto opcional por el puerto `Almacenamiento` con la misma allowlist por magic bytes de las credenciales. Actualizar `Spec_M1`. | **Sí, bio y foto** (2026-09-24). Implementado como Spec_M1 US-7 / FR-ID-027..029: `PUT /api/tutores/me/perfil`, `PUT`/`DELETE /api/tutores/me/foto`, `GET /api/tutores/{id}/foto`, moderación en `DELETE /api/admin/moderacion/tutores/{id}/{bio,foto}`. |
| U2 | ¿Se puede **buscar tutores sin cuenta**? La landing lo promete ("sin tener que registrarte primero") y hoy `/buscar` exige login. Tampoco está en ningún Spec. | **Sí, solo lectura**: buscar y ver perfiles sin cuenta; reservar sí exige cuenta. Requiere endpoints públicos (con el rate limiting de FASE2-02). Si se elige **no**, hay que **corregir el copy** de la landing, que hoy miente. | **No por ahora** (2026-09-24). Se corrige el copy de la landing; el buscador del hero lleva a crear cuenta preservando la búsqueda. |

Si una spec dice **PARAR (Ux)** y la tabla no tiene respuesta, no avances con esa parte:
preguntale al usuario.

## 6. Cómo capturar pantallas (para el "después")

Con el stack arriba (`docker compose up -d`) y datos de prueba (`scripts/seed-*.sh`), usá Playwright
(ya está en `frontend/node_modules`) con un script que haga login por API
(`POST /api/usuarios/login`), inyecte el token en `localStorage` y en la cookie `tinku_jwt`, y saque
`page.screenshot({ fullPage: true })` a 1440×900 y 390×844. Usuarios de prueba: los de
`scripts/seed-usuarios.sh` (password `Password123!`). Para el panel de admin, promové un usuario
de prueba con `tinku.admin.moderacion.ids` (perfil `dev`), **nunca** a mano en una base que no sea
local.
