package com.tinku.identidad.ocr;

/**
 * Una línea de texto detectada por OCR, con su coordenada Y (tope del cuadro
 * delimitador) para reconstruir el orden de lectura de arriba hacia abajo.
 *
 * Lo arma {@link GoogleVisionOcrService} a partir de los bounding boxes de la
 * API; {@link DniParser} lo consume. Mantenerlo simple y desacoplado de Google
 * para que el parser sea testeable en aislamiento (ADR-M1-01).
 */
public record LineaTexto(String texto, double y) {
}
