# M3C-10 — Hallazgos de las pruebas en producción que bloquean el encendido de menores

**Tarea:** T-M3-06j · **Riesgo:** alto (Artículo II) · **Depende de:** nada (independiente de M3C-02..07) · **Commits:** uno o dos por hallazgo (test RED → fix), en el orden de abajo

Fuente: `docs/operacion/HALLAZGOS_pruebas.md` y `docs/operacion/hallazgos_parciales/T3.md`, `T4.md`
(pruebas del 2026-10-14 en `tinku.site`). M3C-09 los exige cerrados antes de encender la flag.

Regla de la auditoría (AGENTS §9), que aplica también acá: cada arreglo lleva un test de regresión que
**falla antes del fix**. Pegá la línea del fallo RED en el mensaje del commit.

---

## H-1 — T3-01 (alta): un menor puede cambiar sus propias capacidades

**Reproducción:** con el JWT del menor `50000002`,
`PATCH /api/usuarios/me/capacidades {"capacidadEstudiante":true,"capacidadAdultoResponsable":false}`
→ 200.

**Causa:** `UsuarioService.actualizarCapacidades` (`backend/src/main/java/com/tinku/identidad/service/UsuarioService.java`)
solo impide que un menor se ponga `capacidadAdultoResponsable=true`. Nada impide que toque la de
estudiante.

**Arreglo:**
1. Excepción nueva `identidad/service/MenorNoModificaCapacidadesException`, mensaje: "Las capacidades
   de un perfil de menor las administra Tinku; no se pueden cambiar desde la cuenta del menor."
2. Al principio de `actualizarCapacidades`, antes de cualquier otra validación:
   `if (usuario.getTipo() == TipoUsuario.MENOR) throw new MenorNoModificaCapacidadesException();`
3. Mapearla a **403** `{"error": ...}` en el `@RestControllerAdvice` de identidad (el que ya tiene los
   otros 403), igual que `MenorNoPerteneceException`.
4. El chequeo existente del menor como Adulto Responsable queda inalcanzable. Dejalo con un comentario
   "defensa en profundidad", no lo borres.

**Tests** (integración, en el test de `UsuarioController` que ya cubre `/me/capacidades`; si no hay,
`identidad/web/CapacidadesIntegracionTest.java`):
- `capacidades_menor_403_yNoCambiaNada`: menor con `capacidadEstudiante=true` intenta
  `{false,false}` y `{true,false}` → 403 las dos veces, y en la BD queda como estaba.
- `capacidades_adulto_sigueFuncionando` (regresión).

**Dato en producción (lo corre el dueño, no el agente):** restaurar a Benjamín. Dejá el SQL en el
cuerpo del commit para que lo copie:
```sql
-- Restaura el valor que tenía antes de la prueba T3-01 (2026-10-14).
UPDATE identidad.usuarios SET capacidad_estudiante = false WHERE dni = '50000002';
```
Antes de escribirlo, confirmá el nombre real de la tabla y la columna en las migraciones (`rg capacidad_estudiante backend/src/main/resources/db/migration`).

Mensajes: `test(identidad): RED de T3-01` y `fix(identidad): un menor no cambia sus capacidades (T3-01, Art. II)`.

---

## H-2 — T4-H1 (alta): la cola de CAP da 403 apenas hay un CAP pendiente

**Reproducción:** con al menos un CAP en `PENDIENTE` o `EN_REVISION_LEGAL`,
`GET /api/admin/moderacion/antecedentes-penales` con el admin de Moderación → 403 sin cuerpo. Con la
cola vacía → 200 `[]`.

**Causa probable** (verificala con el test antes de arreglar):
1. `CertificadoService.colaModeracion()` no es `@Transactional` y devuelve entidades.
2. `CapColaResponse.from` lee `c.getTutor().getNombre()`, una relación LAZY, ya en el controller y sin
   sesión (`open-in-view: false`) → `LazyInitializationException`.
3. Spring hace el dispatch a `/error`, que Spring Security niega → 403 vacío (ver H-4).

**Arreglo:**
1. `CertificadoAntecedentesPenalesRepository`: reemplazá el uso de `findByEstadoInOrderByCreatedAtAsc`
   en la cola por una query con fetch del tutor:
   ```java
   @Query("select c from CertificadoAntecedentesPenales c join fetch c.tutor "
        + "where c.estado in :estados order by c.createdAt asc")
   List<CertificadoAntecedentesPenales> colaConTutor(@Param("estados") Collection<EstadoCap> estados);
   ```
   Es el mismo patrón que la cola de credenciales, que no falla. Dejá el método viejo si otro código lo
   usa (`rg findByEstadoInOrderByCreatedAtAsc`).
2. `colaModeracion()` usa `colaConTutor` y lleva `@Transactional(readOnly = true)`.

**Test:** `AdminCapColaIntegracionTest.cola_conCapPendiente_200_conNombreDelTutor`:
- crear un CAP `PENDIENTE` de un Tutor;
- `GET` con un admin de Moderación → 200, un elemento, `tutorNombre` correcto.
- RED esperado: 403 o 500.

Mensajes: `test(identidad): RED de T4-H1` y `fix(identidad): la cola de CAP carga el tutor (T4-H1)`.

---

## H-3 — TRANS-1 (media): errores de validación devueltos como 403 vacío

**Reproducción** (de los informes): JSON mal formado, enum inválido, parámetro faltante, UUID mal
formado o archivo de más de 5 MB → 403 `content-length: 0` en vez de 400/413. Pasa incluso en
`/api/usuarios/login`.

**Causa** (confirmada en `T3.md`): ante esas excepciones Spring hace un dispatch `ERROR` a `/error`.
Ese dispatch no pasa por el filtro JWT (`OncePerRequestFilter` no corre en `ERROR` por defecto), y
`SecurityConfig` no permite `/error` → `anyRequest().authenticated()` lo niega → 403 vacío.

**Arreglo** en `backend/src/main/java/com/tinku/config/SecurityConfig.java`, dentro de
`authorizeHttpRequests`, como **primera** regla:
```java
// TRANS-1: el dispatch de error de Spring (400/413/404 de MVC) no debe convertirse en 403.
// /error solo muestra el status y el mensaje del error original; no expone datos.
.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
```
- Verificá en `application.yml` que `server.error.include-stacktrace` y `include-message` no
  filtren datos internos. Si `include-message` está en `always`, revisá que ningún mensaje de
  excepción tenga datos sensibles; si hay duda, dejalo en `never` y que los 400 lleven el cuerpo por
  `@ExceptionHandler` (punto siguiente).
- Para que el 400 tenga el formato `{"error": ...}` del resto de la API, **extendé el `ValidacionErrorHandler`
  que ya existe** (`backend/src/main/java/com/tinku/config/ValidacionErrorHandler.java`, `@RestControllerAdvice`
  global con `@Order(LOWEST_PRECEDENCE)`, hoy solo maneja `MethodArgumentNotValidException`) con estos handlers,
  sin crear una clase nueva:

  | Excepción | Status | Mensaje |
  |---|---|---|
  | `HttpMessageNotReadableException` | 400 | "El cuerpo de la solicitud no es válido." |
  | `MethodArgumentTypeMismatchException` | 400 | "El parámetro «{nombre}» no es válido." |
  | `MissingServletRequestParameterException` | 400 | "Falta el parámetro «{nombre}»." |
  | `MaxUploadSizeExceededException` | 413 | "El archivo supera el tamaño máximo permitido." |

**Tests:** `config/ErroresHttpIntegracionTest.java` (archivo nuevo):
- `login_jsonMalFormado_400_conCuerpo`;
- `reserva_uuidMalFormado_400`;
- `horarios_sinFecha_400`;
- `foto_masDe5MB_413`;
- `rutaProtegida_sinJwt_sigue403` (regresión: permitir `/error` no abre nada).
- Correr la suite completa: si algún test esperaba el 403 viejo, actualizalo citando TRANS-1 en un
  comentario. No borres tests.

Mensajes: `test(config): RED de TRANS-1` y `fix(config): los errores de validación dejan de verse como 403 (TRANS-1)`.

---

## H-4 — T3-03 (media): el menor abre pantallas de otros roles en la UI

**Reproducción:** con la sesión del menor `50000001` se abren en la UI:
- `/cuenta/menores`, con "Sumar a un hijo o hija";
- `/cuenta/clases-con-menores`;
- `/cuenta/horarios`, con "Guardar disponibilidad";
- `/cuenta/cobros`;
- `/admin`, que queda en "Cargando colas…".

Por API todo da 403. Es superficie confusa y defensa en profundidad.

**Arreglo** (frontend):
1. `frontend/src/lib/navegacion.ts`, o donde viva la lista de rutas: una función pura
   `rutaPermitida(pathname: string, payload: PayloadSesion): boolean`.
   - Para `tipo === "MENOR"` solo permite `/cuenta`, `/cuenta/perfil`, `/cuenta/acceso`,
     `/cuenta/reservas` y `/cuenta/reservas/*`, `/cuenta/notificaciones`, `/buscar`, `/tutores/*` y
     `/aula/*`.
   - **Antes de fijar la lista**, revisá con `rg "href=\"/" src/app src/components` qué rutas usa hoy
     la vista del menor. No le quites ninguna que ya use legítimamente; si dudás de una, preguntá.
2. `frontend/src/app/cuenta/layout.tsx`: si `!rutaPermitida(pathname, sesion.payload)`, hacer
   `router.replace("/cuenta")` y no renderizar `children`.
3. `frontend/src/app/admin/layout.tsx` o `ContextoAdmin.tsx`: si `GET /api/admin/yo` responde 403, no
   quedarse en "Cargando…". Mostrar "Esta sección es solo para el equipo de Tinku." con un link a
   `/cuenta`. Esto arregla también el hallazgo T5-1 para Estudiantes y Tutores.
4. `cuenta/layout.tsx` no tiene que pedir `/api/tutores/me/estado-perfil` si el usuario no es Tutor
   (hallazgo T7-M3): buscá con `rg estado-perfil src` dónde se llama y condicionalo a `tipo === "TUTOR"`.

**Tests:** `frontend/tests/cuenta/menor-rutas.spec.ts`, con la sesión falsa de un menor (helper
`setFakeSession` con `tipo: "MENOR"`):
- `/cuenta/menores`, `/cuenta/horarios` y `/cuenta/cobros` redirigen a `/cuenta`;
- `/cuenta/reservas` sí carga;
- `/admin` con un Estudiante y `/api/admin/yo` mockeado en 403 muestra el mensaje, no "Cargando";
- tests puros de `rutaPermitida`.

Mensajes: `test(front): RED de T3-03` y `fix(front): el menor no entra a pantallas de otros roles (T3-03, T5-1, T7-M3)`.

---

## Verificación del conjunto

- Backend: `./mvnw -B test` con JDK 21 → `Tests run:` ≥ baseline + los nuevos, 0 failures, 0 errors;
  cobertura ≥ 85,6 %.
- Frontend: `npm run lint`, `npm run build` y `npx playwright test`.
- En `docs/operacion/HALLAZGOS_pruebas.md`: marcar T3-01, T4-H1, TRANS-1 y T3-03 (y T5-1 y T7-M3,
  arreglados de paso) como **cerrados**, con el commit, en el mismo commit del último fix.
- Tildá T-M3-06j en las dos listas de tareas.

## NO tocar

- Los demás hallazgos de `HALLAZGOS_pruebas.md`: tienen su propio orden fuera de este chunk.
- El acento del 403 de admin (T5-2) y el resto de las bajas.
