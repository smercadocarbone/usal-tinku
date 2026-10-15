# Hallazgos de las pruebas exploratorias en `tinku.site`

> Ejecución de [`PLAN_pruebas_produccion.md`](PLAN_pruebas_produccion.md) el 2026-10-14 sobre
> `https://tinku.site` y `https://api.tinku.site`, commit `f83114a` de `main`, perfil `seed-demo`
> (`TINKU_PERFILES`). Siete testers independientes (T1–T7), uno por rol, cada uno con su sesión.
> El detalle de cada uno (pasos, esperado, obtenido, evidencia) está en
> [`hallazgos_parciales/`](hallazgos_parciales/). Los hallazgos de este documento **no están verificados
> de forma independiente por el consolidador**: son lo que informó cada tester; las causas marcadas
> "probable" son inferencias leídas del código, no confirmadas con logs.

## 1. Veredicto

**0 bloqueantes.** No se encontró ninguna fuga de datos entre cuentas, ningún salto de rol ni de
autenticación, y el corte fail-closed de menores aguanta (Art. II). Hay **3 hallazgos de severidad alta**
(§3) y un patrón transversal de errores 403 sin cuerpo (§4) que conviene arreglar antes del piloto.

## 2. Condiciones previas

| # | Condición | Resultado |
|---|-----------|-----------|
| P1 | Todo mergeado a `main` | ✅ 0 PR abiertos, `main` en `f83114a` (PR #84) |
| P2 | `Deploy - produccion` verde | ✅ run 52 en `success` |
| P3 | El sitio sirve la versión nueva | ✅ `actuator/health` en `UP` |
| P4 | El entorno alcanza los dos hosts | ✅ tras agregarlos a *Allowed domains* (la primera corrida se cortó acá) |
| P5 | Hay datos de prueba | ✅ camino A (`TINKU_PERFILES=prod,seed-demo`); admins `99900301` (moderacion_seguridad) y `99900302` (soporte_financiero) |

## 3. Hallazgos de severidad alta

| ID | Rol | Hallazgo | Esperado / obtenido |
|----|-----|----------|---------------------|
| T3-01 | Menor | Un menor puede cambiarse sus propias capacidades: `PATCH /api/usuarios/me/capacidades` con su JWT pone `capacidadEstudiante:true` y responde 200. | Debe dar 403 si el usuario es menor. Con esa capacidad igual no pudo reservar (403) ni crear Solicitud (409), así que la restricción de fondo aguantó; pero no se revierte por API (`false/false` da 422). **Benjamín `50000002` quedó modificado en producción.** |
| T4-H1 | Moderación | `GET /api/admin/moderacion/antecedentes-penales` da 403 sin cuerpo en cuanto hay un CAP pendiente o en revisión legal (con la cola vacía da 200 `[]`). Reproducido 5 veces. | La cola de CAP no se puede usar desde `/admin/antecedentes`; solo se revisa por ID con `PATCH`. Causa probable: `CapColaResponse.from` lee `c.getTutor()` (LAZY) fuera de transacción con `open-in-view:false`. |
| T2-H1 | Tutor | El Tutor no puede quitar ni desactivar franjas: solo existe `POST /api/tutores/franjas`, sin DELETE ni PUT, y la UI es de solo lectura. | Falta la baja. **El Spec M4 tampoco la define**, así que además es un hueco del spec. |

## 4. Hallazgos de severidad media

| ID | Rol | Hallazgo |
|----|-----|----------|
| TRANS-1 | Todos | **Errores de validación devuelven 403 con cuerpo vacío** en vez de 400/413: JSON malformado, enum inválido, parámetro faltante, número desbordado, UUID malformado, foto de más de 5 MB, `fecha=basura`, `evidenciaUrl` largo, `estado=FOO`. Visto por T1, T2, T3, T4, T5 y T6, incluso en `/api/usuarios/login`. Parece un problema de permisos y oculta el error real. Probable causa común: el manejo de errores cae al entry point de Spring Security. |
| T1-M1 | Estudiante | La búsqueda devuelve tutores para consultas sin relación: "clases de tango bailarín" da 17, "asdkjh qwezxc" da 3, "a" da 24. Los scores (0,34–0,47) pasan el umbral 0,3 y la UI nunca muestra "no encontramos". Contradice US-1 de M2. |
| T1-M2 | Estudiante | Los Tutores `30111222` a `30555666` tienen 15 franjas de 1 h seguidas por día y las contiguas no se unen: solo se reservan 30 o 60 min; 90, 120 y 180 dan 422. |
| T1-M3 | Estudiante | `GET /api/tutores/{id}/horarios?duracionMinutos=180` devuelve 90 bloques duplicados y desordenados, con inicios fuera de franja como `isAvailable:true` (reservarlos da 422). Causa probable: `HorariosDisponiblesService.horariosDelDia` suma minutos a un `LocalTime` y pasa la medianoche. La UI calcula sus propios bloques y no lo muestra. |
| T2-H2 | Tutor | La API acepta una franja puntual con fecha pasada (2020-01-01); la UI lo impide. |
| T2-H3 | Tutor | `diaSemana=9` devuelve 500. |
| T2-H10 / T1 | Tutor, Estudiante | El perfil público lista las franjas crudas como "Disponibilidad esta semana", incluidas horas ya pasadas que `/horarios` marca no disponibles y que `POST /api/reservas` rechaza ("Faltan menos de 30 minutos"). |
| T3-03 | Menor | En la UI el menor abre pantallas de otros roles (alta de hijos en `/cuenta/menores`, "Quiero dar clases a menores", editor de horarios, `/admin` en "Cargando…"). Por API todo da 403. |
| T4-H4 | Moderación | Además de TRANS-1: los enums van en minúscula en denuncias y en MAYÚSCULA en credencial y CAP. |
| T4-H5 | Moderación | `/admin/salud` muestra "No autorizado" para este rol: el front consulta `/api/admin/financiero/pasarela` (403), aunque `GET /api/admin/salud` da 200 y el menú ofrece el ítem. |
| T4-H7 | Moderación | La cola de denuncias (API y UI) no expone `evidenciaUrl`, aunque la US-4 pide ver caso, evidencia y descargo juntos. El denunciado aparece solo como `#8chars`. |
| T4-H3 | Moderación | Con un único admin de Moderación, las denuncias donde él es parte quedan sin salida en su propia cola; no hay derivación ni recusación. |
| T5-H4 | Soporte Fin. | **A confirmar:** `GET /api/admin/financiero/precios-regionales` devuelve `[]` y `/admin/precios` muestra solo el formulario. Si producción no tiene precios cargados, el Tutor no tiene referencia de precio. |
| T7-M1 | Tutor | `/cuenta/horarios` hace scroll horizontal en 360×640 (446 px contra 360) y la grilla solo muestra Lun/Mar/Mié. |
| T7-M2 | Tutor | `/cuenta/materias` hace scroll horizontal (388 px): los chips de "Tus temas" se pasan del card. |
| T7-M3 | Responsable | `/cuenta/clases-con-menores` pide `/api/tutores/me/estado-perfil` a una cuenta que no es Tutor: 403 y 2 errores en consola. |

## 5. Hallazgos de severidad baja

- **Consola y UX por rol:** `GET /api/admin/yo` da 403 en cada pantalla de un no-admin (T1, T2, T7). Un Estudiante o Tutor que abre `/admin` o `/admin/pagos` ve el shell del panel; `/admin/pagos` dice "No hay devoluciones pendientes" aunque la cola dio 403 (T5-1). El admin que entra termina en `/cuenta/reservas` en vez de `/admin` (T4-H10). `/admin/pagos` y `/admin/precios` se renderizan por URL para Moderación, aunque la API protege (T4-H6).
- **Textos:** el 403 de admin sale con acentos rotos (`acci?n de administraci?n`; probable `SecurityConfig.java:160` con `getWriter()` sin UTF-8) (T5-2). Mensajes de validación en inglés ("must not be null", "Invalid UUID string") y sin tildes ("calificacion", "vencio") (T1, T2-H9).
- **Moderación:** el 403 por conflicto de interés dice "requiere rol de Moderación…", falso (T4-H2). Un segundo descargo pisa el anterior (H8). En la UI, "Fundada" preselecciona Advertencia y un clic basta (H9). Re-rechazar un CAP sin categoría borra el antecedente ya informado (H11). Algunos 404 sin cuerpo `{"error"}` (H13). El 400 sale antes que el 403 en endpoints financieros (H12).
- **Estudiante:** las búsquedas guardadas pierden los filtros, aceptan duplicados y no se pueden borrar. El picker rotula "Ocupado" a bloques pasados. Una clase cancelada o finalizada sigue ofreciendo "Escribir el pedido". Cancelar una reserva ajena da 403 y verla da 404. `confirmar-retorno` con un paymentId falso da 503 "MercadoPago no está disponible", engañoso. Precio máximo "-5" se convierte en 5 sin aviso. `filtro_materia` distingue mayúsculas y tildes. El perfil no muestra comentarios de calificaciones (a confirmar contra FR-REP-001).
- **Tutor:** Mis cobros muestra totales en $0 con 10 filas "Cobrado $10.950" (coherente con el código, que excluye pagos simulados de los totales). El neto de Cobros ($10.950) y el de Precio ($10.044) difieren porque Cobros no descuenta MP. Con 6139 la UI sigue mostrando el neto. `updatedAt` de la tarifa siempre viejo. La bio acepta email y teléfono (por diseño, con moderación posterior; el `<script>` queda escapado).
- **Responsable y menor:** al AR el piloto sin menores se le avisa recién al confirmar, tras 3 pasos (T3-02). `GET /api/pagos/tarifa` responde 200 a un menor (T3-05). Solo Lorena (`31000028`) tiene CAP en el seed; Sofía, María, Lucas y Natalia dan 409 al autorizarlos aunque el seed ya los dejó autorizados (T3-06).
- **Seguridad de cabeceras (T6):** falta `Content-Security-Policy` en el front; falta HSTS en `api.tinku.site`; un UUID malformado en la URL da 403 en vez de 400.
- **Transversal (T7):** títulos de pestaña repetidos en casi todas las pantallas; enlaces de 18–23 px de alto y un switch de 13×20 px; contraste 3,39:1 a 12 px en la grilla de horarios; dos `h1` en `/cuenta/seguridad`; "Franjas publicadas" son unas 150 filas sin agrupar; foco poco visible en algunos campos (a confirmar a ojo).
- **PWA (T7):** el manifest es válido, pero no hay service worker (`/sw.js` 404), ni íconos PNG 192/512 ni maskable, ni `/favicon.ico` ni `/apple-icon.png`; orientación fijada en portrait. Instalabilidad real sin confirmar.

## 6. Qué anduvo bien

- **Seguridad (T6):** 29 rutas protegidas sin JWT dan 403; Actuator, `/v3/api-docs` y Swagger cerrados; acceso horizontal por ID da 404 sin revelar dueño; roles cruzados dan 403 (doble capa); JWT con firma alterada, `alg=none` y malformados rechazados; rate limit 429 desde el 5º login; login y recuperar-password no enumeran usuarios; CORS rechaza orígenes ajenos.
- **Menores (T3):** reservar y crear Solicitud con un menor dan 409 `MENORES_PILOTO` por API y UI sin crear nada; el menor no puede pagar, autorizar ni denunciar por API; el AR solo autoriza a sus menores y exige CAP vigente.
- **Reservas (T1):** ventana mínima exacta al segundo (30:05 acepta, 29:55 rechaza); horario ocupado rechazado en todos los solapamientos; el timeout de pago libera el horario; con el resumen apagado la UI no lo ofrece.
- **Precio (T2):** el neto coincide a mano: $15.000 → $10.044/h, $6.140 (piso) → $4.111,34/h, $20.000 → $13.392/h; el piso se aplica igual en API y UI.
- **Moderación (T4):** conflicto de interés, anonimato del denunciante, validaciones de denuncia y CAP (BR-CAP-01/02), sin XSS en el descargo.
- **Permisos financieros (T5):** el admin financiero recibe 403 en todo `/api/admin/moderacion/*`, y viceversa.
- **UX (T7):** voseo consistente, sin claves i18n ni inglés visible, LCP de 0,27 a 2,4 s, labels y `lang="es-AR"` correctos.

## 7. Qué NO se pudo probar y por qué

- **Pago, confirmación, webhook y conciliación:** la pasarela está en cobro real (`habilitada=true`, `bypassPermitido=false`) y la preferencia es de MercadoPago real. Por la regla de dinero del plan, se detuvo ahí.
- **Todo lo que depende de una reserva `confirmada`:** cancelar una confirmada, reprogramar, pedido de reprogramación (camino positivo), calificar con éxito (las sesiones del seed ya estaban calificadas), mis cobros reales.
- **Aula en vivo (paso 4 del plan):** requiere una reserva pagada y confirmada, que no se puede lograr sin tarjeta real. No se ejecutó.
- **Menores en sesión:** aula, kill-switch en vivo, pizarra, nota y pedidos con menor. Dependen de `tinku.menores.sesiones-habilitadas=true` y de T-M3-06 (pendiente). La rama del kill-switch con menor se revisó solo en código.
- **Soporte Financiero:** reintentar reembolso, liberar escrow, reembolso parcial, "resuelto manual" (mueven plata real, y las colas están vacías); `PATCH /pasarela` y `POST /precios-regionales` (escriben configuración).
- **Moderación:** alertas de kill-switch reales, denuncias con `sesionId` y pausa de escrow, sanciones temporal y definitiva, moderación de bio y foto, backoff de credenciales, vencimiento de CAP, jobs de 48 hs, auditoría, emails.
- **Otros:** registro con OCR de DNI real, fuerza bruta de contraseñas, `verificar-dni`, fuzzing/SQLi, confiabilidad de `CF-Connecting-IP`, instalabilidad PWA real, lectores de pantalla, zoom 200 %, modo oscuro, throttling.

## 8. Residuos que dejó la prueba en producción

Ningún tester modificó código ni configuración. Quedó este estado en datos del seed:

| Residuo | Detalle | Cómo limpiarlo |
|---------|---------|----------------|
| Benjamín `50000002` con `capacidadEstudiante:true` | Efecto de T3-01; originalmente `false`. No se revierte por API. | SQL, o resetear el seed |
| Franja puntual `2020-01-01 05:00–06:00`, id `d2015987-09a5-44e5-9be2-07c5cd66a913`, Tutor `31000030` | Creada por T2 al probar H2; sin endpoint de baja (T2-H1). Está en el pasado, no afecta reservas. | SQL |
| Dos denuncias `en_revision`: `154ab2d0-f4fb-436f-9661-9d8f2bd91e3d` y `9422a2c7-a2fc-4246-af6c-31b64cf3e695` | Creadas por T4; nadie puede resolverlas porque el admin es parte. | SQL, o un segundo admin de Moderación |
| Lorena Aguirre (`31000028`) autorizada para Benjamín; el Tutor `31000028` con CAP aprobado 12 meses (`habilitadoParaMenores=true`) | Inertes mientras `sesiones-habilitadas=false`. T4 lo nombró "Daniela Quiroga", pero en el seed `31000028` es Lorena y Daniela es `31000027`: confirmar contra la base. | SQL |
| Advertencias fundadas a Pablo Villalba (`31000029`) y Natalia Cáceres (`31000030`); credenciales y CAP aprobados/rechazados en ambos | Creados por T4. | SQL |
| 3 búsquedas guardadas en `40000001` | Sin endpoint de borrado. | SQL |
| Reservas de prueba (T1, T2, T3) | Todas `cancelada`, ninguna pagada. | — |
| Una preferencia de pago real en MercadoPago | Creada por T1 sobre una reserva ya vencida; no se pagó. | Vence sola |

## 9. Propuesta de arreglos, priorizada

Según `AGENTS.md` §9, cada arreglo va con un test de regresión que falle antes del fix, en una rama aparte, y no se mergea sin que lo pidas.

1. **T3-01** (alta, Art. II): `PATCH /me/capacidades` debe dar 403 a un menor. Restaurar a Benjamín.
2. **T4-H1** (alta): hacer que la cola de CAP cargue el Tutor con `join fetch` (como la de credenciales) o dentro de transacción.
3. **TRANS-1** (media, transversal): un único `@ExceptionHandler` o `AuthenticationEntryPoint` que mapee `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` y `MaxUploadSizeExceededException` a 400/413 con cuerpo `{"error"}`. Resuelve de un golpe T1, T2-H3/H4, T3-04, T4-H4, T5-3 y T6.
4. **T2-H1:** definir la baja de franjas (agregar al Spec M4) e implementarla; mientras tanto no hay forma de corregir una franja errónea.
5. **T1-M1:** subir el umbral de similitud o agregar un piso relativo para que "sin coincidencia exacta" aparezca (US-1 de M2).
6. **T1-M2/M3:** unir franjas contiguas al calcular duraciones y corregir el desborde de medianoche en `HorariosDisponiblesService`.
7. **T2-H10/T2-H2/T2-H3:** filtrar franjas pasadas en el perfil público; validar fecha futura y `diaSemana` 1–7 en la API.
8. **T5-H4:** confirmar si `precios-regionales` está vacío en producción y cargar los precios de referencia.
9. **T4-H5/H7/H3, T3-03, T5-1, T7-M3:** correcciones de UI por rol (guard de ruta por rol, no pedir `admin/yo` ni `estado-perfil` a quien no corresponde, `evidenciaUrl` en la cola de denuncias, segundo admin o recusación).
10. **T7-M1/M2:** corregir el overflow móvil de `/cuenta/horarios` y `/cuenta/materias`.
11. **Bajas:** acentos del 403 de admin (UTF-8), CSP y HSTS de la API, textos en inglés, PWA (service worker, íconos 192/512, favicon), títulos por pantalla.

## 10. Estado de cierre (remediación, `PLAN_remediacion_hallazgos.md`)

| Hallazgo | Estado | Rama | Test que lo cubre |
|---|---|---|---|
| T3-01 | **Cerrado** (falta restaurar a Benjamín en prod: O-6) | `fix/rem-a` | `CapacidadesMenorIntegracionTest` |
| T4-H1 | **Cerrado** | `fix/rem-a` | `AdminCapColaIntegracionTest` |
| TRANS-1 (T1-4, T2-H4, T3-04, T4-H4 parte, T5-3, T6-19) | **Cerrado** | `fix/rem-a` | `ErroresHttpIntegracionTest` (servidor real) |
| T3-03, T5-1, T7-M3 | **Cerrado** | `fix/rem-a` | `frontend/tests/cuenta/menor-rutas.spec.ts` |
| T2-H1 | **Cerrado** (Spec M4 FR-RES-025) | `fix/rem-d` | `AgendaFranjasIntegracionTest.quitar_*`, `agenda-rem-d.spec.ts` AGENDA-003 |
| T1-M2, T1-M3 | **Cerrado** | `fix/rem-d` | `AgendaFranjasIntegracionTest.horarios_*`, AGENDA-006 |
| T2-H2, T2-H3 | **Cerrado** | `fix/rem-d` | `AgendaFranjasIntegracionTest.publicar_*` |
| T2-H10, T1-8 | **Cerrado** | `fix/rem-d` | `AgendaFranjasIntegracionTest.disponibilidad_*`, AGENDA-004/005/007 |
| T1-9 | **Cerrado** | `fix/rem-d` | AGENDA-008 |
| T1-12 (fecha pasada y precio negativo) | **Cerrado** | `fix/rem-d` | `reservar_horarioPasado_422_yaPaso`, AGENDA-009 |
| T7-3 | **Cerrado** | `fix/rem-d` | AGENDA-001/002 |
| T1-5, T2-H9 (mensajes en inglés y sin tildes) | **Cerrado** | `fix/rem-b` | `RobustezApiIntegracionTest.validacion_mensajesEnCastellano` |
| T5-2 (403 de admin sin UTF-8) | **Cerrado** | `fix/rem-b` | `admin403_enUtf8` |
| T4-H4 (enums) | **Cerrado** | `fix/rem-b` | `enums_sinDistinguirMayusculas` |
| T1-10 (403/404 y paymentId inexistente) | **Cerrado** | `fix/rem-b` | `reservaAjena_404_enVerCancelarYReprogramar`, `MercadoPagoClientHttpTest.getPago_inexistenteEnMp_noEsMpNoDisponible` |
| T2-H8 (tarifa) | **Cerrado** | `fix/rem-b` | `tarifa_redondeaYActualizaFecha` |
| T3-05 (tarifa a un menor) | **Cerrado** | `fix/rem-b` | `tarifa_soloTutor` |
| T6-4 (mensaje del guard) | **Cerrado** | `fix/rem-b` | `soloParticipante_mensajeSegunLaAccion` |
| T2-H11 (contacto en la bio) | **Cerrado** (D-7) | `fix/rem-b` | `bio_ocultaDatosDeContacto`, `OcultadorDeContactoTest` |
| T1-7, T2-H12 (`/api/admin/yo` en cada pantalla) | **Backend cerrado** (`rolAdmin` en `/me`); el front deja de llamarlo en REM-G | `fix/rem-b` | `me_informaElRolDeAdmin` |
| T4-H13 (404 sin cuerpo en admin) | **Cerrado** | `fix/rem-b` | `admin404_conCuerpo` |
| T4-H2, T4-H3 (conflicto de interés) | **Cerrado** (D-5; el segundo admin es la acción O-7) | `fix/rem-f` | `AdminModeracionRemIntegracionTest.conflictoDeInteres_*`, ADMIN-F-006 |
| T4-H5 (salud para Moderación) | **Cerrado** | `fix/rem-f` | ADMIN-F-003 |
| T4-H6, T4-H10 (secciones por rol, inicio del admin) | **Cerrado** | `fix/rem-f` | ADMIN-F-001/002/004 |
| T4-H7 (evidencia y denunciado en la cola) | **Cerrado** | `fix/rem-f` | `colaAdmin_traeEvidenciaYDenunciado_sinDenunciante`, ADMIN-F-005 |
| T4-H8 (descargos) | **Cerrado** (D-6, `V48`) | `fix/rem-f` | `descargos_seConservanEnElHistorial` |
| T4-H9 (sanción preelegida) | **Cerrado** | `fix/rem-f` | ADMIN-F-007 |
| T4-H11 (categoría del CAP) | **Cerrado** | `fix/rem-f` | `cap_reRechazarSinCategoria_conservaLaCategoria` |
| T4-H12 (400 antes que 403) | **Cerrado** | `fix/rem-f` | `rolEquivocado_403AntesQueValidacion` |
| T5-4 (precios vacíos) | **Cerrado** (cargar los precios es O-8) | `fix/rem-f` | ADMIN-F-008 |
| T1-M1 (relevancia) | **Cerrado** (D-3; calibrado con el modelo real, `CALIBRACION_busqueda.md`; revisar `MATCHING_SCORE_MINIMO` en Coolify) | `fix/rem-e` | `BusquedaRemIntegracionTest.textoSinLetras*`, `corteRelativo_yAbsolutoCalibrado`, BUSCAR-E-001 |
| T1-6 (búsquedas guardadas) | **Cerrado** (`V49`) | `fix/rem-e` | `guardada_conservaFiltros_noDuplica_yseBorra`, BUSCAR-E-002 |
| T1-11 (comentarios en el perfil) | **Cerrado** (D-4) | `fix/rem-e` | `ComentariosTutorIntegracionTest`, BUSCAR-E-003 |
| T1-12 (filtro de materia) | **Cerrado** | `fix/rem-e` | `filtroMateria_sinDistinguirMayusculasNiTildes` |
| T7-M1, T7-M2 (móvil) | **Cerrado** | `fix/rem-g` | `remediacion-g.spec.ts` REM-G-001/002 |
| T7-5 (títulos) | **Cerrado** | `fix/rem-g` | REM-G-003 |
| T7-7 (PWA) | **Cerrado en el alcance de D-8** (íconos 192/512/maskable, apple-icon, favicon; sin service worker) | `fix/rem-g` | — |
| T7-8 (objetivos táctiles) | **Cerrado** | `fix/rem-g` | REM-G-004 |
| T7-9, T7-10 (contraste y foco) | **Cerrado**. El banner de Términos queda primero: es el primero también a la vista (orden de foco = orden visual) y es lo pendiente más importante | `fix/rem-g` | REM-G-005/006 |
| T7-12 (dos h1) | **Cerrado** | `fix/rem-g` | REM-G-007 |
| T2-H5, T2-H6, T2-H7 (cobros y precio) | **Cerrado** | `fix/rem-g` | REM-G-008/009 |
| T6 (CSP) | **En observación**: `Content-Security-Policy-Report-Only`; pasar a aplicar es O-12 | `fix/rem-g` | REM-G-010 |
| T1-7, T2-H12 (`/api/admin/yo`) | **Cerrado** (front usa `rolAdmin` de `/me`) | `fix/rem-g` | REM-G-011 |
| T3-06 (Tutores autorizados sin CAP en el seed) | **Cerrado** para bases nuevas; en la base actual, los CAP se crean al próximo arranque con `seed-demo` (el runner es idempotente) | `fix/rem-h` | `SeedDemoIntegracionTest.losTutoresAutorizadosDeLosMenoresTienenCapVigente` |
| T1-2 (franjas de 1 h del seed) | **Cerrado** para bases nuevas (35 franjas de 3 h); una base con el seed viejo conserva las suyas, que con REM-D ya se unen al reservar | `fix/rem-h` | `lasFranjasDelSeedSonDeTresHoras` |
