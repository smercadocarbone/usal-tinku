package com.tinku.identidad.ocr;

/**
 * Lee el texto de un título o certificado (ADR-M1-06, triaje automático). Mismo Tesseract que el
 * DNI ({@link TesseractOcrService}); en dev/test, {@link StubLectorTextoDocumento}. Nunca persiste
 * ni loggea el contenido (Constitución, Art. V).
 */
public interface LectorTextoDocumento {

    TextoDocumento leer(byte[] contenido);
}
