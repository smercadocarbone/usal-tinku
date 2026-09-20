package com.tinku.identidad.service;

/** El token no existe, ya se usó o venció — un solo mensaje para los tres
 * casos: no hay que revelarle al cliente cuál de ellos fue. */
public class TokenResetInvalidoException extends RuntimeException {
    public TokenResetInvalidoException() {
        super("El enlace no es válido o ya expiró. Solicitá uno nuevo.");
    }
}
