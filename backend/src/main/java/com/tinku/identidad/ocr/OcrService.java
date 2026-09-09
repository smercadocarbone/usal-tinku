package com.tinku.identidad.ocr;

/**
 * Puerto hacia el proveedor de verificación de documento.
 *
 * ADR-M1-01 (Plan_M1_Identidad_Perfiles.md, sección 4) TODAVÍA NO ESTÁ
 * RESUELTO — candidatos: Google Cloud Vision, AWS Textract, o un servicio
 * especializado en verificación de identidad. Esta interfaz existe
 * precisamente para que el resto del código (UsuarioService) no dependa
 * de cuál se termine eligiendo: el día que se resuelva el ADR, se agrega
 * una implementación nueva (ej. `GoogleVisionOcrService`) y se cambia el
 * bean activo por perfil de Spring — no se toca nada más.
 *
 * NUNCA loggear ni persistir la imagen del documento más allá de lo
 * estrictamente necesario para esta llamada (Constitución, Artículo V —
 * minimización de datos).
 */
public interface OcrService {
    /**
     * Procesa la foto del documento. {@code datosDeclarados} lleva lo que el
     * usuario dijo en el formulario: el proveedor real lo ignora (todo sale de
     * la imagen), y {@link StubOcrService} lo usa para fabricar un resultado
     * de dev/test coherente con lo declarado.
     */
    ResultadoOcr procesarDocumento(byte[] imagenDocumento, DatosDniDeclarados datosDeclarados);
}
