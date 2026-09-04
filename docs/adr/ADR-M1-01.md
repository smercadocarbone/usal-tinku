# ADR-M1-01 — Proveedor de OCR: Tesseract vía Tess4J (in-process)

## Estado
Aceptado (Chunk M1-B). Decisión tomada con investigación de costos y de arquitectura propia. El `StubOcrService` queda reservado a `dev`/`test`; la implementación real (Tess4J) se activa en el perfil de producción, sin requerir cuenta de facturación ni credenciales.

## Contexto
M1 (Identidad y Perfiles) necesita OCR para leer el DNI en el alta de usuario (US-1/US-2/US-3, Plan sección 2.1). Hasta acá había un `StubOcrService` interino en `dev`/`test`. Hay que resolver el proveedor real (filas "OCR" del Registro de Decisiones Técnicas de la Constitución) antes de integrar el flujo de registro de producción.

## Decisión
**Tesseract OCR vía Tess4J**, corriendo **en el mismo proceso Java** del monolito, con el paquete de idioma español (`spa.traineddata`). Se descartó Google Cloud Vision (decisión previa de este ADR, revocada — ver Historial).

Motivos:
- **Costo cero real:** Tesseract es open source y no requiere cuenta de facturación ni tarjeta. A diferencia de Vision/Textract, que exigen una *billing account* (y tarjeta) incluso para usar el free tier, Tesseract no introduce ningún costo ni dependencia de proveedor externo.
- **Minimización de datos superior (Artículo V):** la imagen del DNI **nunca sale del servidor** — se procesa in-process. Es el máximo alineamiento posible con la privacidad de datos de menores (Ley 25.326) y con la minimización que la Constitución exige.
- **No agrega un proceso nuevo:** a diferencia de una alternativa Python tipo PaddleOCR, que habría requerido una **segunda excepción al Artículo VIII** (monolito, ya hay una excepción para el motor de matching). Tess4J entra como una dependencia Java más dentro del monolito existente.

## Alternativas descartadas / consideradas
- **Google Cloud Vision**: exige billing account + tarjeta incluso para free tier; manda la imagen fuera del servidor (choca con Artículo V); devuelve texto crudo igual — igual habría que parsear. Descartado por costo operativo y privacidad (posición previa de este ADR, revocada).
- **AWS Textract / Document AI**: Document AI y Textract `AnalyzeID` no soportan DNI argentino (solo EE.UU./Francia) — confirmado, no asumido. Descartados.
- **Veriff/Onfido y especializados en verificación de identidad**: cobran por verificación + mínimos — fuera de presupuesto USD 0-100/mes y de escala de Tinku. Descartados.
- **PaddleOCR (Python)**: requeriría una segunda excepción al Artículo VIII (proceso nuevo) y mantenimiento de un segundo runtime. Descartado por simplicidad (Artículo VII).

## Riesgo aceptado (explícito)
Tesseract tiene **menor precisión que Vision** en fotos de celular (ángulo, luz, reflejo) — es el riesgo principal de exactitud.

**Mitigación:**
1. **Preprocesamiento de imagen** antes de pasarla a Tesseract: **deskew + mejora de contraste**, con una librería liviana o procesamiento propio, como componente separado y testeable (`PreprocesadorImagen`) — mismo patrón que el parser de campos.
2. **Parsing de campos propio** (`DniParser`) aislado y testeado contra **ambos formatos de DNI argentino vigentes** (libreta pre-2009 y tarjeta plástica 2009+) — ningún motor de OCR argentino devuelve campos estructurados de DNI (ya confirmado).
3. **Red de contención ya existente en el Spec**: reintentos + backoff (FR-ID-011) y canal de soporte (M8) para los casos que sigan fallando tras el preprocesamiento.

## Revisión futura
Si en el **piloto** la tasa de "documento ilegible" resulta demasiado alta pese al preprocesamiento, **reabrir este ADR** para reconsiderar Vision con guardas de presupuesto estrictas (tarjeta virtual + alerta de gasto). **No** reabrir antes de tener datos reales del piloto.

## Implementación
- Dependencia **Tess4J** en el `pom.xml` + binario nativo de Tesseract instalado como **paquete del sistema** (documentado en README: Linux/Docker `apt install tesseract-ocr tesseract-ocr-spa`, Mac `brew install tesseract`).
- **`spa.traineddata`** (idioma español) descargado y documentado dónde vive (carpeta de recursos o volumen), vía `TESSDATA_PREFIX`.
- `TesseractOcrService implements OcrService`, activo en el **perfil de producción** (NO en `dev`/`test` — ahí sigue `StubOcrService`), sin gating por credenciales (no requiere ninguna).
- `PreprocesadorImagen` (deskew + contraste) y `DniParser` (texto→campos) como componentes aislados y testeables.

## Consecuencias
- La imagen del DNI nunca abandona el servidor (fortalece Artículo V y la privacidad de menores).
- El servidor necesita el binario nativo de Tesseract disponible en el entorno de despliegue — nuevo requisito de infraestructura (documentado en README/Dockerfile).
- `StubOcrService` queda formalmente como implementación de desarrollo/prueba, nunca de producción.
- T-M1-04 queda implementado y verificable a nivel de componentes (parser + preprocesamiento con tests unitarios); la verificación end-to-end contra el binario nativo requiere el entorno con Tesseract instalado.

## Historial de este ADR
- **(anterior) Vision:** una versión previa de este ADR eligió Google Cloud Vision. Se revocó por: billing account + tarjeta exigida incluso para free tier, envío de la imagen fuera del servidor (Artículo V) y costo de integración. Reemplazada por la decisión Tesseract de esta versión.

## Registro de Decisiones Técnicas (Constitución)
Actualiza la fila "OCR" del Registro: **Tesseract vía Tess4J (in-process)** — decidida vía este ADR el 2026-09-04, frente a Vision/Textract/servicios especializados.
