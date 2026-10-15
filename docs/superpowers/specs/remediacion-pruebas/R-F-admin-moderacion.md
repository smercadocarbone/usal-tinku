# R-F — Admin y moderación

**Tarea:** T-REM-F · **Rama:** `fix/rem-f-admin` · **Depende de:** chunk A (H-2 cola de CAP, H-4 guard de `/admin`) y B (B-10: `rolAdmin` en `/me`) · **Decisiones:** D-5, D-6
Fuente: `hallazgos_parciales/T4.md` (H2–H13), `T5.md` (1, 3, 4). Migración nueva: **`V48__m9_descargo_historial.sql`**
(si otro chunk ya tomó el 49, usá el siguiente libre: `ls backend/src/main/resources/db/migration | sort -V | tail -1`).

## F-1 Mensaje falso en el 403 por conflicto de interés (T4-H2) y salida mínima para el admin único (T4-H3) — Dec D-5

**Qué pasa:** `AdminModeracionGate.exigirNoEsParteDelCaso` (`backend/src/main/java/com/tinku/admin/`) lanza
`AccesoModeracionDenegadoException`, y el handler (`AdminExceptionHandler.accesoDenegado`, `SeguridadExceptionHandler.handleProhibido`)
responde "No autorizado para esta acción de administración." / "…requiere rol de Moderación y Seguridad", que es falso:
el admin sí tiene el rol, lo que pasa es que es parte del caso.

**Arreglo:**
1. `ConflictoDeInteresException extends AccesoModeracionDenegadoException` (archivo nuevo en `shared/`), con mensaje
   "No podés resolver un caso en el que sos parte. Lo tiene que resolver otro admin de Moderación."
2. `exigirNoEsParteDelCaso` lanza la nueva. Los handlers de los dos módulos la mapean **antes** que a la clase base
   (Spring elige el más específico) a **403** con ese mensaje y `"codigo": "CONFLICTO_DE_INTERES"`.
3. La cola de denuncias (`ColasModeracionController`) agrega a cada fila `conflicto: boolean` (el admin que consulta es
   denunciante o denunciado). La UI (`ColaDenuncias.tsx`) deshabilita "Resolver" en esas filas y muestra
   "Sos parte de este caso: lo tiene que resolver otro admin." En la fila no se muestra el nombre del denunciante.
4. **No hay flujo de reasignación**: lo cubre tener un segundo admin (Ola 0, O-7).
**Tests (RED):** IT `ConflictoDeInteresIntegracionTest`: admin denunciado intenta resolver → 403 con `CONFLICTO_DE_INTERES` y
el mensaje nuevo; la cola trae `conflicto:true` solo en sus casos; un segundo admin resuelve el mismo caso → 200.

## F-2 `/admin/salud` pide un endpoint que el rol no tiene (T4-H5)

`frontend/src/app/admin/salud/page.tsx` (línea ~24) hace `Promise.all([getSaludSistema(), getPasarelaEstado()])`: la pasarela
es del rol Soporte Financiero (`/api/admin/financiero/pasarela` → 403 para Moderación) y rompe toda la pantalla.
Arreglo: `getPasarelaEstado()` solo si `rolAdmin === "soporte_financiero"` (viene de `usePerfil`); si no, la tarjeta de pasarela
no se muestra y `getSaludSistema()` carga sola. **Test (RED):** Playwright con rol `moderacion_seguridad` y
`/api/admin/financiero/pasarela` mockeado en 403: la pantalla muestra la salud y no "No autorizado".

## F-3 La cola de denuncias no muestra la evidencia ni a quién se denunció (T4-H7)

`Spec_M9` US-4 / FR-SEC-007 pide ver caso, evidencia y descargo juntos. `DenunciaResponse`
(`backend/src/main/java/com/tinku/seguridad/web/`) no trae `evidenciaUrl` y muestra al denunciado como `#8chars`.
1. **No** se agrega nada a `DenunciaResponse` (es la vista del denunciado y del denunciante; la identidad del denunciante
   nunca viaja, FR-SEC-006). Crear `DenunciaAdminResponse` con `DenunciaResponse` + `evidenciaUrl`, `denunciadoNombre`,
   `denunciadoApellido`, `denunciadoTipo`. **Nunca** el id ni el nombre del denunciante.
2. `ColasModeracionController` devuelve `DenunciaAdminResponse`; el tipo `DenunciaCola` de `frontend/src/lib/api.ts` y
   `ColaDenuncias.tsx` muestran el nombre y un enlace "Ver evidencia" (`target="_blank" rel="noopener noreferrer"`; la
   URL se valida como `https?://` antes de renderizarla).
**Tests (RED):** IT: la cola admin trae `evidenciaUrl` y el nombre del denunciado, y el JSON **no contiene** `denuncianteId`;
`GET /api/denuncias/recibidas` (vista del denunciado) sigue sin `evidenciaUrl`. Playwright: el enlace existe y tiene `rel`.

## F-4 El segundo descargo pisa al primero (T4-H8) — Dec D-6

`DenunciaService` (método de descargo, línea ~183) reemplaza `descargo_texto`. Con la decisión recomendada se conservan los dos.
1. `V48__m9_descargo_historial.sql`:
   ```sql
   ALTER TABLE seguridad.denuncias ADD COLUMN descargo_historial TEXT;
   UPDATE seguridad.denuncias SET descargo_historial = descargo_texto WHERE descargo_texto IS NOT NULL;
   ```
2. Al recibir un descargo: si ya había uno, se agrega al historial con fecha
   (`"[2026-10-15 14:30] texto"`, separados por `\n---\n`) y `descargo_texto` queda con el último; cada texto sigue limitado a 300
   caracteres (FR-SEC-006). `DenunciaAdminResponse` incluye `descargoHistorial`.
**Tests (RED):** dos descargos → `descargoHistorial` contiene los dos con sus fechas; el denunciado ve su último; un tercero → 403.

## F-5 "Fundada" preselecciona una sanción sin confirmar (T4-H9)

`frontend/src/components/admin/ColaDenuncias.tsx` (línea ~61): `tipoSancion` arranca en `"advertencia"` y un clic compromete
una acción irreversible. Cambios: estado inicial `null`; el botón "Confirmar" queda deshabilitado hasta elegir una sanción;
al confirmar, un diálogo "¿Resolver como fundada con {sanción} para {nombre}? No se puede deshacer." (`Cancelar` /
`Sí, resolver`). **Test (RED):** Playwright: sin elegir sanción no se puede confirmar; elegir y confirmar muestra el diálogo y
recién al aceptar se llama a `POST …/resolver`.

## F-6 El admin va a `/cuenta/reservas` al entrar (T4-H10) y ve pantallas de otro rol (T4-H6)

1. `inicioPorRol` (`frontend/src/lib/sesion.ts`) devuelve `/admin` si el `/me` trae `rolAdmin` (B-10). `login/page.tsx:38` ya lo usa.
2. Guard por rol en `frontend/src/app/admin/layout.tsx`: `/admin/pagos`, `/admin/precios` solo para `soporte_financiero`;
   `/admin/antecedentes`, `/admin/credenciales`, `/admin/denuncias`, `/admin/alertas`, `/admin/temas-sugeridos` solo para
   `moderacion_seguridad`; `/admin/salud` y `/admin/tickets` para ambos. Otro rol → "Esta sección es de {rol}" con enlace a `/admin`.
   Función pura `rutaAdminPermitida(pathname, rol)` en `src/lib/navegacion.ts`.
**Tests (RED):** función pura con tabla (12 combinaciones); Playwright: Moderación en `/admin/pagos` ve el mensaje y no hace
llamadas a `/api/admin/financiero/*`.

## F-7 Re-rechazar un CAP borra el antecedente informado (T4-H11)

`CertificadoService.revisar` (`backend/src/main/java/com/tinku/identidad/service/`, líneas ~141–142) hace siempre
`setTieneAntecedentes(categoria != null)` y `setCategoriaAntecedente(categoria == null ? null : …)`. Un CAP que ya estaba
`EN_REVISION_LEGAL` con categoría y se rechaza **sin** categoría la pierde.
Arreglo: si `categoria == null` y el CAP ya tenía categoría, conservar los dos campos. Solo se limpian al **aprobar** sin categoría.
**Test (RED):** CAP `EN_REVISION_LEGAL` con categoría `OTRO` → `RECHAZAR` sin categoría → queda `RECHAZADO` con `categoriaAntecedente=OTRO`.

## F-8 400 antes que 403 en los endpoints financieros (T4-H12)

El gate por rol se evalúa dentro del cuerpo del método, **después** de `@Valid`: un cuerpo vacío con el rol equivocado da 400
(y filtra el esquema del request). Mover el chequeo de rol a la cadena de filtros:
en `SecurityConfig`, junto a `.requestMatchers("/api/admin/**").access(adminActivoAuthorizationManager)`, agregar **antes**:
`/api/admin/financiero/**` → admin activo con rol `SOPORTE_FINANCIERO`; `/api/admin/moderacion/**` → rol `MODERACION_SEGURIDAD`.
Reutilizá `AdminActivoAuthorizationManager` con un parámetro de rol (buscá la clase con `rg -l "class AdminActivoAuthorizationManager"`).
El gate de servicio (`AdminModeracionGate`) **se mantiene** (defensa en profundidad).
**Tests (RED):** con JWT de Moderación, `POST /api/admin/financiero/reembolso-parcial` con `{}` → 403 (hoy 400); con JWT
financiero, `GET /api/admin/moderacion/denuncias` → 403; cada rol sigue entrando a lo suyo (regresión sobre los IT de admin existentes).

## F-9 Estados vacíos de precios (T5-H4)

`/admin/precios` (`components/admin/PreciosRegionales.tsx`) solo muestra el formulario cuando la lista es `[]`. Agregar
"Todavía no hay precios cargados. Cargá al menos el de cada provincia donde haya Tutores: es la referencia que ven al ponerse una tarifa."
**Test:** Playwright con `[]` → el texto aparece. (La carga de datos es la acción O-8 del plan.)

## Verificación del chunk

- Backend `./mvnw -B test`; frontend `npm run lint && npm run build && npx playwright test tests/admin`.
- Repetir con el script de T4 contra un entorno de pruebas los casos H2, H5, H7, H8, H9, H11, H12.
- Marcar cerrados: T4-H2, H3 (mínimo), H5–H12, T5-4, T5-3 (si B-3 lo cubrió, confirmar); tildar T-REM-F.

## NO tocar

- El anonimato del denunciante (FR-SEC-006) ni las reglas de sanción.
- El gate por servicio de `AdminModeracionGate`.
