package com.tinku.identidad.dto;

/**
 * Formato de email que se acepta al registrarse o cambiarlo: algo@dominio.ext. El {@code @Email}
 * de Jakarta acepta "ana@gmail" (sin extensión), que nunca recibe un mail. Mismo criterio que
 * {@code esEmailValido} del frontend.
 */
public final class FormatoEmail {

    public static final String REGEX = "^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)*\\.[A-Za-z]{2,}$";

    private FormatoEmail() {
    }
}
