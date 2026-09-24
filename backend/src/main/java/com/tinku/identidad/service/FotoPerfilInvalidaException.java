package com.tinku.identidad.service;

/** U1: la foto de perfil no es PNG ni JPEG por su contenido real (magic bytes). */
public class FotoPerfilInvalidaException extends RuntimeException {
    public FotoPerfilInvalidaException() {
        super("La foto tiene que ser una imagen JPG o PNG.");
    }
}
