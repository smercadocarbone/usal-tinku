# T04 — Verificación automática de la firma digital del CAP (opcional)

**Branch:** `tesis/cap-firma` · **Riesgo:** bajo · **Bloqueada por:** T02 y una decisión futura del usuario (PT2 resolvió: no en el MVP)

## 1. Contexto

El CAP es un PDF con firma digital del Registro Nacional de Reincidencia. La tesis (Cap. 6,
riesgo R-10) identifica como riesgo residual un certificado adulterado. Verificar la firma
automáticamente lo reduce, pero requiere dependencias nuevas (Apache PDFBox y BouncyCastle), que
**exigen un ADR previo** (A5).

## 2. Decisión resuelta (PT2 — 2026-09-23): **manual en el MVP**. Esta spec queda como mejora futura.

Recomendación: **no** en el MVP. El Admin verifica la firma manualmente al abrir el PDF (T02,
paso 5). Esta spec se ejecuta solo si el usuario decide automatizar.

## 3. Implementación (si se aprueba)

1. **ADR-M1-05** — dependencias PDFBox + BouncyCastle, alcance, alternativas (verificación
   manual; servicio externo de validación de firmas).
2. Al cargar el CAP: verificar que la firma es válida, que el certificado firmante corresponde al
   Registro Nacional de Reincidencia y que el documento no fue modificado después de firmarse.
3. Firma inválida → el CAP queda `rechazado` con motivo "firma inválida"; firma válida → sigue a
   revisión manual (**la verificación no reemplaza la revisión del contenido**).

## 4. Tests

- PDF firmado de prueba (válido), PDF modificado después de firmar y PDF sin firma.
