package com.tinku.identidad.service;

import java.time.Duration;

/**
 * El ciclo de intentos de OCR se agotó y la persona está en espera de
 * 24hs (FR-ID-011). Devuelve hasta cuándo debe esperar para que el
 * cliente lo muestre.
 */
public class DocumentoEnBackoffException extends RuntimeException {

    private final Duration esperaRestante;

    public DocumentoEnBackoffException(Duration esperaRestante) {
        super("Demasiados intentos de verificación. Volvé a intentar en "
                + esperaRestante.toHours() + " horas.");
        this.esperaRestante = esperaRestante;
    }

    public Duration getEsperaRestante() {
        return esperaRestante;
    }
}
