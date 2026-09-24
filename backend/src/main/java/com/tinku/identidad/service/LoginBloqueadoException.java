package com.tinku.identidad.service;

import java.time.Duration;

/** FASE2-02: demasiados intentos fallidos de login para un DNI. Mismo mensaje exista o no el DNI. */
public class LoginBloqueadoException extends RuntimeException {

    private final Duration espera;

    public LoginBloqueadoException(Duration espera) {
        super("Demasiados intentos. Probá de nuevo más tarde.");
        this.espera = espera;
    }

    public Duration getEspera() {
        return espera;
    }
}
