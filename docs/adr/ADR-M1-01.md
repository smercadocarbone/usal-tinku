# ADR-M1-01 — Banco de OCR: StubOcrService interino

## Estado
**INTERINO — stub de dev/test ÚNICAMENTE, decisión real de proveedor PENDIENTE para Chunk M1-B. NO USAR EN PRODUCCIÓN BAJO NINGUNA CIRCUNSTANCIA.**

## Contexto
M1 (Identidad y Perfiles) necesita OCR para leer el DNI en el alta de usuario. Mientras no haya un proveedor real seleccionado, el código ya tiene un `StubOcrService` activo en los perfiles `dev`/`test` que devuelve siempre un resultado fijo, para poder probar el resto del flujo de registro sin depender de una cuenta externa.

## Decisión (provisoria)
Se usa `StubOcrService` en `dev`/`test` únicamente: `@Service @Profile({"dev", "test"})`. Devuelve un `ResultadoOcr` fijo (`documentoLegible=true`, DNI `00000000`, "NOMBRE STUB"/"APELLIDO STUB"). Está diseñado para que, al resolver el proveedor real, esta clase quede solo para dev/test y la implementación real se active en los demás perfiles.

## Restricciones de esta decisión provisional
- **Nunca** activar este bean en un perfil que no sea `dev`/`test`.
- **No** es ni pretende ser una implementación de producción: no llama a ningún proveedor, no maneja imagen real, no tiene telemetría ni validación de calidad de captura.
- El `@Profile` es la ÚNICA barrera que impide que caiga en producción — frágil por diseño mientras no haya proveedor real. No construir features de producción encima de este stub.

## Consecuencias / pendiente
- Pendiente: decidir proveedor de OCR real (fila "OCR" del Registro de Decisiones Técnicas de la Constitución — pendiente, ADR) para **Chunk M1-B**, y resolver este ADR en ese momento.
- Mientras tanto, cualquier test que dependa del resultado exacto del stub documenta esa dependencia (ver `UsuarioServiceTest`).
