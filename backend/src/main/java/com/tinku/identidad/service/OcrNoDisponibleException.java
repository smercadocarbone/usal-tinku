package com.tinku.identidad.service;

/**
 * El proveedor de lectura de documentos (OCR) falló en sí mismo (binario
 * nativo, biblioteca de idioma, servicio externo): distinto de que haya
 * leído la foto y no encontrara datos útiles (ese caso es
 * {@link DocumentoIlegibleException} y sí consume reintentos FR-ID-011).
 *
 * No es culpa del usuario ni de la foto: NUNCA debe consumir el ciclo de 3
 * intentos + backoff — reintentar unos minutos después sí tiene sentido.
 */
public class OcrNoDisponibleException extends RuntimeException {
    public OcrNoDisponibleException() {
        super("El lector de documentos no está disponible en este momento. Intentá de nuevo en unos minutos.");
    }
}