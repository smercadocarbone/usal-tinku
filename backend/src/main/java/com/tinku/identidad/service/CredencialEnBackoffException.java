package com.tinku.identidad.service;

import java.time.Duration;

/**
 * El ciclo de reintentos de Credencial Académica se agotó y el Tutor está en
 * espera escalada (FR-ID-012: 24hs * 2^n → 24→48→96…). Devuelve el tiempo
 * restante para que el cliente lo muestre.
 */
public class CredencialEnBackoffException extends RuntimeException {

    private final Duration esperaRestante;

    public CredencialEnBackoffException(Duration esperaRestante) {
        super("Alcanzaste el límite de intentos de carga de credencial. Volvé a intentar en "
                + esperaRestante.toHours() + " horas.");
        this.esperaRestante = esperaRestante;
    }

    public Duration getEsperaRestante() {
        return esperaRestante;
    }
}
