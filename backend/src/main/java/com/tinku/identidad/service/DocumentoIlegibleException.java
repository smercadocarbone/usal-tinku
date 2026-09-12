package com.tinku.identidad.service;

/**
 * El OCR no pudo leer el documento (distinto de que lo haya leído y no
 * coincida). Este es el caso que consume el contador de 3 intentos +
 * backoff de 24hs (FR-ID-011) — un rechazo por edad o DNI duplicado NO
 * consume reintentos, porque no tiene sentido "reintentar" ser mayor de
 * edad (ver Plan_M1_Identidad_Perfiles.md, sección 2.1, paso 7).
 */
public class DocumentoIlegibleException extends RuntimeException {
    public DocumentoIlegibleException() {
        super("No pudimos leer tu documento en la foto. Intentá de nuevo con más luz, sin reflejos y bien encuadrado.");
    }
}
