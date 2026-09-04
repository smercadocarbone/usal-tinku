package com.tinku.identidad.service;

/**
 * BR-CONSENT-01: el alta de un menor requiere un consentimiento explícito
 * y separado del T&C general. Si no se otorga, no se crea la cuenta.
 */
public class ConsentimientoNoOtorgadoException extends RuntimeException {
    public ConsentimientoNoOtorgadoException() {
        super("Se requiere el consentimiento explícito para el tratamiento de datos del menor.");
    }
}
