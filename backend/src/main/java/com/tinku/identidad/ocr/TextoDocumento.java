package com.tinku.identidad.ocr;

/**
 * Texto leído de un documento que no es el DNI (ADR-M1-06: el título o certificado del Tutor).
 *  - {@code LEIDO}: hay texto.
 *  - {@code SIN_TEXTO}: la imagen se pudo procesar y no tiene texto legible → rechazo inmediato.
 *  - {@code NO_PROCESABLE}: no se pudo leer (p. ej. un PDF, o el lector no está disponible). No
 *    rechaza: lo decide el Admin, y el nombre queda "sin comparar".
 */
public record TextoDocumento(Lectura lectura, String texto) {

    public enum Lectura { LEIDO, SIN_TEXTO, NO_PROCESABLE }

    public static TextoDocumento leido(String texto) {
        return new TextoDocumento(Lectura.LEIDO, texto);
    }

    public static TextoDocumento sinTexto() {
        return new TextoDocumento(Lectura.SIN_TEXTO, null);
    }

    public static TextoDocumento noProcesable() {
        return new TextoDocumento(Lectura.NO_PROCESABLE, null);
    }
}
