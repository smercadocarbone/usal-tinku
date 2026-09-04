# NOTAS_VERIFICACION — branch `chunk/m1-c`

Chunk M1-C: **T-M1-06** (alta de menor) + **T-M1-07** (backoff de OCR).
Este branch NO fue mergeado a main y NO marca ningún chunk/tarea como cerrado
en `Tasks_Tinku_Chunks.md` ni `Tasks_Tinku_Implementacion.md` — el entorno de
esta sesión no puede correr los tests de integración que esas marcas certifican.

## Qué quedó implementado

- **T-M1-06 — `POST /api/usuarios/menores`** (autenticado, ejecutado por el
  Adulto Responsable — Artículo II / FR-ID-020):
  - `RegistroMenorRequest` con `consentimientoExplicito` separado del T&C
    (BR-CONSENT-01) y validación `@AssertTrue`.
  - `UsuarioService.registrarMenor(...)`: mismo OCR que adulto (FR-ID-019),
    edad ≥ 6 y < 18 (FR-ID-017, sobre la fecha extraída del documento),
    unicidad de DNI (FR-ID-019), límite de 5 menores (FR-ID-013), set de
    `tipo=MENOR` + `adultoResponsable` + persistencia del consentimiento en la
    misma transacción (BR-CONSENT-01).
  - `ConsentimientoMenor` + `ConsentimientoMenorRepository` (tabla
    `consentimientos_menor`, ya existente en V2).
  - `UsuarioRepository.countByAdultoResponsableIdAndTipo` para FR-ID-013.
- **T-M1-07 — Backoff persistido de OCR** (FR-ID-011, 3 intentos por ciclo,
  24hs de espera):
  - Migración **V4__m1_intentos_ocr.sql** (tabla `intentos_ocr`). Se agrega una
    migración NUEVA (V4), no se editó V2 (AGENTS.md §7).
  - `IntentoOcr` + `IntentoOcrRepository`.
  - `OcrBackoffService`: `chequearPuedeIntentar(dni)` (lanza
    `DocumentoEnBackoffException` si está en espera) y
    `registrarIntentoFallido(dni)` (acumula; al 3ro fija cooldown de 24hs y
    resetea el contador). Cableado en `registrarAdulto` Y `registrarMenor`.
  - Decisiones: el cooldown es PASIVO (timestamp persistido comparado al leer),
    por lo que NO se agrega un job de Quartz — no es un callback que haya que
    despachar, es un estado persistido (Constitución Art. IV/X).
  - `tinku.ocr.backoff-horas` (default 24) en `application.yml`.
- Excepciones nuevas: `DocumentoEnBackoffException` (HTTP 429),
  `ConsentimientoNoOtorgadoException` (422), `LimiteMenoresAlcanzadoException`
  (422) + handlers en `IdentidadExceptionHandler`.

## Unit tests corridos en esta sesión (verdes, sin Docker/Tesseract)

- `OcrBackoffServiceTest` (6): ciclo, cooldown 24hs, reset, backoff activo/pasado.
- `UsuarioServiceRegistroUnitTest` (9): alta de menor exitosa, edad <6, edad ≥18,
  sin consentimiento, límite de 5, documento ilegible (consume backoff), nombre no
  coincide, DNI duplicado, y `registrarAdulto` respeta backoff.
- Regresión: `DniParserTest` (7) + `PreprocesadorImagenTest` (6) del M1-B.
- Total: 28 tests verdes. TODOS son unitarios con mocks — ninguno levanta el
  contexto Spring ni requiere Docker.

## Qué falta correr/confirmar en un entorno con Docker + Tesseract

1. **Migraciones Flyway**: correr la app con Postgres y confirmar que V4 aplica
   (`flyway_schema_history`) y que `ddl-auto:validate` pasa contra el esquema
   real (las tablas `intentos_ocr` y `consentimientos_menor`).
2. **Tests de integración Testcontainers** (`@SpringBootTest`) que NO corrieron
   por falta de Docker — entre ellos el pre-existente `UsuarioServiceTest`
   (US-1 adulto) y los de configuración/seguridad. Hay que confirmar que el
   nuevo `UsuarioService` (constructor ampliado) sigue autowiriendo en el
   contexto completo.
3. **Alta de menor E2E** (`POST /api/usuarios/menores`): requeriría una US-2
   integración — no está escrita en este chunk (ver M1-G, T-M1-13).
4. **Tesseract real** NO se usó acá: toda prueba de OCR usó un `OcrService`
   **mockeado** o el `StubOcrService`. Falta confirmar que el flujo completo
   con `TesseractOcrService` (perfil prod) devuelve fechas/edades/coincidencia
   en un DNI real de prueba (edad de menor 6-17 es una rama que el stub no
   ejerce de verdad).

## Nota de seguridad (Artículo II)

El alta de menor exige sesión del Adulto Responsable (endpoint autenticado). El
menor NO se registra a sí mismo. Falta, en futuros chunks, cerrar la restricción
de permisos del menor sobre pagar/autorizar Tutores/presentar Denuncias (se hace
a nivel endpoint en los módulos correspondientes — M4/M9).
