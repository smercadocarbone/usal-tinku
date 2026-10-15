# R-B — Robustez de la API: mensajes, enums, cabeceras de error y consistencia

**Tarea:** T-REM-B · **Rama:** `fix/rem-b-api` · **Depende de:** chunk A (TRANS-1 ya cerrado: este chunk no repite los handlers de 400/413) · **Decisión:** D-7 (B-9)

Cada ítem: test RED primero, fix después, un commit por ítem como mínimo. Fuentes: `HALLAZGOS_pruebas.md` y
`hallazgos_parciales/T1.md … T6.md`.

## B-1 Mensajes de validación en español rioplatense (T1-5, T2-H9)

**Qué pasa:** `"tutorId: must not be null"`, `"nuevoHorario: must not be null"`, `"paymentId: must not be blank"`:
las anotaciones sin `message` usan el texto por defecto de Hibernate Validator, que sale en inglés porque el
contenedor no fija idioma. `ValidacionErrorHandler` (`backend/src/main/java/com/tinku/config/`) devuelve
`fe.getDefaultMessage()` tal cual.

**Arreglo:**
1. `backend/src/main/resources/ValidationMessages.properties` (archivo nuevo, UTF-8) con voseo para las claves
   que usa el proyecto. Buscá cuáles con `rg -o "@(NotNull|NotBlank|NotEmpty|Size|Min|Max|Pattern|Email|Positive|DecimalMin|Past|Future)\b" backend/src/main/java | sort | uniq -c`.
   Mínimo:
   ```
   jakarta.validation.constraints.NotNull.message=es obligatorio.
   jakarta.validation.constraints.NotBlank.message=no puede estar vacío.
   jakarta.validation.constraints.NotEmpty.message=no puede estar vacío.
   jakarta.validation.constraints.Size.message=tiene que tener entre {min} y {max} caracteres.
   jakarta.validation.constraints.Min.message=tiene que ser como mínimo {value}.
   jakarta.validation.constraints.Max.message=tiene que ser como máximo {value}.
   jakarta.validation.constraints.Email.message=no es un email válido.
   jakarta.validation.constraints.Positive.message=tiene que ser mayor que cero.
   ```
2. Forzar el idioma para que no dependa del contenedor: en `application.yml`, bajo `spring:`,
   `web: { locale: es-AR, locale-resolver: fixed }`. Ojo: ya existe un bloque `web:` bajo `management`; no es el
   mismo, es otra clave de primer nivel.
3. Mensajes de excepciones propias sin tildes: `rg -n '"[^"]*\b(calificacion|vencio|edicion|sesion|informacion|ventana de edicion)\b' backend/src/main/java`
   (hay al menos `CalificacionNoEncontradaException`, `CalificacionDefinitivaException`,
   `CalificacionOcultaNoEditableException`). Corregilos con tildes.
4. **No** cambies mensajes que un test o el front comparen por texto: buscá con
   `rg "calificacion vencio|must not be" backend/src/test frontend`. El front decide por `codigo` (R5), no por texto.

**Tests (RED):** `config/MensajesValidacionIntegracionTest`: `POST /api/reservas` con `{}` → 400 y
`campos.tutorId` igual a "es obligatorio."; ningún valor de `campos` contiene "must" ni "Invalid"; el mensaje de
`CalificacionDefinitivaException` contiene "venció".

## B-2 UTF-8 en el 403 de admin (T5-2)

`SecurityConfig.adminAccesoDenegadoHandler()` (línea ~157) hace `response.setContentType("application/json")` y
`getWriter().write(...)` sin charset: los acentos llegan como `?`. Arreglo: `response.setCharacterEncoding("UTF-8")`
y `setContentType("application/json;charset=UTF-8")`. Test: `GET /api/admin/yo` con un JWT de Estudiante →
`Content-Type` con `charset=UTF-8` y el cuerpo contiene "administración" (bytes UTF-8 correctos).

## B-3 Enums sin sensibilidad a mayúsculas, con mensaje útil (T4-H4)

Denuncias aceptan solo minúscula (`acoso`) y credencial/CAP solo MAYÚSCULA. Arreglo en `application.yml`:
`spring.jackson.mapper.accept-case-insensitive-enums: true`. Para parámetros de query (`?estado=hecho`) agregá un
`Converter<String, EstadoReembolsoAdicional>` case-insensitive (buscá el enum con
`rg "reembolsos-adicional" backend/src/main/java/com/tinku/admin`). Cuando el valor no existe, el 400 del chunk A
ya responde; agregá al mensaje la lista de valores aceptados si el handler lo permite
(`MethodArgumentTypeMismatchException#getRequiredType().getEnumConstants()`).
Tests: `motivo:"ACOSO"` y `"acoso"` ambos 201; `?estado=FOO` → 400 con "valores aceptados".

## B-4 403 vs 404 al cancelar una reserva ajena (T1-10)

`GET /api/reservas/{id}` ajena da 404 y `POST /api/reservas/{id}/cancelar` ajena da 403 (confirma que el id
existe). Unificar en **404 "Reserva no encontrada."**. Mismo criterio en `reprogramar` y `preferencia`
(`rg "SoloPagadorPreferenciaException|SoloParticipante" backend/src/main/java/com/tinku/{reservas,pagos}`): el
403 solo si el recurso es del usuario pero le falta una capacidad; si no es suyo, 404.
Test: estudiante B contra la reserva de A: `GET`, `cancelar` y `reprogramar` → 404 los tres.

## B-5 `confirmar-retorno` con un paymentId inexistente da 503 (T1-10)

`MercadoPagoNoDisponibleException` ("MercadoPago no está disponible en este momento") se usa también cuando MP
responde 404. Distinguir: 404 de MP → 422 "No encontramos ese pago en MercadoPago."; timeouts/5xx de MP → 503.
Ubicá el cliente con `rg "MercadoPagoNoDisponibleException" backend/src/main/java/com/tinku/pagos` (el que
mapea la respuesta HTTP). Test con el mock del cliente de MP que ya usa `PagoServiceTest`/`PagoIntegracion*`.

## B-6 Tarifa: `updatedAt` viejo y decimales (T2-H8)

`GET /api/pagos/tarifa` devuelve siempre el `updatedAt` original y `PUT 6140.005` responde 3 decimales mientras
el GET da `6140.01`. Revisá `TarifaTutor` (entidad en `backend/src/main/java/com/tinku/pagos/model`): el
`updatedAt` tiene que actualizarse en cada guardado (`@PreUpdate` o `setUpdatedAt(Instant.now())` en el
servicio) y el servicio tiene que `setScale(2, RoundingMode.HALF_UP)` **antes** de validar contra el piso y de
guardar. Test: `PUT 6140.005` → la respuesta y el GET siguiente muestran `6140.01`, y `updatedAt` del GET es
posterior al anterior.

## B-7 `GET /api/pagos/tarifa` responde 200 a un menor (T3-05)

`PagoController.miTarifa` no exige ser Tutor. Mismo guard que usa `PUT /tarifa` (`SoloTutorException` → 403).
Test: menor y Estudiante → 403; Tutor → 200.

## B-8 Mensaje del guard de participantes (T6-4)

`SoloParticipanteException` dice "Solo los participantes de la sesión pueden finalizarla." aunque se use en
`GET /por-reserva`, `killswitch`, `clasificador`, `token`. Parametrizar: constructor con el verbo ("ver",
"entrar a", "usar") y ajustar cada llamador (`rg "new SoloParticipanteException"`). Test: el GET devuelve
"Solo los participantes de la sesión pueden verla."

## B-9 Contacto en la bio del Tutor (T2-H11) — **depende de D-7**

Con la decisión recomendada: al guardar `PUT /api/tutores/me/perfil`, reemplazar por `[contacto oculto]` los
emails, teléfonos (≥ 8 dígitos seguidos o con separadores) y `@usuario`, y responder con un campo
`avisos: ["Quitamos datos de contacto de tu presentación: las clases se coordinan dentro de Tinku."]` que la UI
muestre. Función pura `OcultadorDeContacto` con tests tabla (casos válidos e inválidos: "tengo 15 años de
experiencia" no se toca; "mi cel es 11 5555-1234" sí). La moderación posterior queda como está.

## B-10 `GET /api/admin/yo` en cada pantalla (T1-7, T2-H12)

Cada pantalla de un no-admin pide `/api/admin/yo` (el front lo hace en `lib/usePerfil.ts:66`) y recibe 403.
Arreglo: `UsuarioResponse` (`GET /api/usuarios/me`) suma `rolAdmin` (`String | null`:
`moderacion_seguridad`, `soporte_financiero` o null) leyendo `adminRepo.findByUsuario_DniAndActivoTrue`. El
front (chunk G, G-9) deja de llamar a `/api/admin/yo`. **Este endpoint se conserva** (lo usan los paneles).
Test: `/me` de un Estudiante → `rolAdmin: null`; de `99900301` → `"moderacion_seguridad"`.

## B-11 404 sin cuerpo en endpoints admin (T4-H13)

`/credenciales/{id}/resolver`, `/alertas/{id}/clip`, `/tutores/{id}/bio` devuelven 404 sin `{"error"}`. Agregar
los handlers de `…NoEncontradaException` en `AdminExceptionHandler` con el mismo formato que M9. Test por
endpoint con un id inexistente.

## Verificación del chunk

- `./mvnw -B test` (JDK 21): `Tests run:` ≥ baseline + nuevos, 0 failures, 0 errors.
- Marcar en `HALLAZGOS_pruebas.md` cerrados: T1-5, T1-7, T1-10, T2-H8, T2-H9, T2-H11, T3-05, T4-H4, T4-H13, T5-2, T6-4.
- Tildar T-REM-B en las dos listas.

## NO tocar

- Los handlers de 400/413 del chunk A.
- El contrato de `/api/admin/yo`.
