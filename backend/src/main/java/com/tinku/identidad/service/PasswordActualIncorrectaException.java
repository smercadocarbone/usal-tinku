package com.tinku.identidad.service;

/**
 * Distinta de {@link org.springframework.security.authentication.BadCredentialsException}
 * (login): esa se mapea a 401 y el frontend interpreta CUALQUIER 401 como
 * sesión vencida (fuerza logout, ver `manageSesion` en `lib/api.ts`). Acá el
 * usuario SÍ está autenticado — solo escribió mal su contraseña actual — así
 * que reusar 401 lo desconectaría por error. Se mapea a 403.
 */
public class PasswordActualIncorrectaException extends RuntimeException {
    public PasswordActualIncorrectaException() {
        super("La contraseña actual no es correcta.");
    }
}
