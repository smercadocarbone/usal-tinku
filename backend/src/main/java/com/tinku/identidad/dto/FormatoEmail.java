package com.tinku.identidad.dto;

/**
 * Formato de email que se acepta al registrarse o cambiarlo: algo@dominio.ext. El {@code @Email}
 * de Jakarta acepta "ana@gmail" (sin extensión), que nunca recibe un mail. Mismo criterio que
 * {@code esEmailValido} del frontend.
 */
public final class FormatoEmail {

    public static final String REGEX = "^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)*\\.[A-Za-z]{2,}$";

    /** Tope de una dirección de email (RFC 5321); la columna es VARCHAR(255). */
    public static final int MAX = 254;

    private FormatoEmail() {
    }

    /** Un email es uno solo sin importar mayúsculas: se guarda y se compara en minúsculas. */
    public static String normalizar(String email) {
        return email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
