package com.tinku.identidad.service;

/**
 * AUD-007: la credencial subida no es PDF, PNG ni JPEG según su contenido real
 * (magic bytes). Se traduce a 422 y el archivo no llega a guardarse.
 */
public class ArchivoCredencialInvalidoException extends RuntimeException {
    public ArchivoCredencialInvalidoException() {
        super("La credencial tiene que ser un PDF, PNG o JPEG.");
    }
}
