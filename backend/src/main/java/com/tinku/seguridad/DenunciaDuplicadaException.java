package com.tinku.seguridad;

/** Ya hay una denuncia igual (mismo denunciante, denunciado y clase) en revisión (409). */
public class DenunciaDuplicadaException extends RuntimeException {
    public DenunciaDuplicadaException() {
        super("Ya presentaste esta denuncia y está en revisión.");
    }
}
