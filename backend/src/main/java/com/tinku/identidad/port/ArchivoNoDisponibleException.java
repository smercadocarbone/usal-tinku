package com.tinku.identidad.port;

/**
 * La referencia de un archivo no se puede servir: no existe, no es de este
 * almacenamiento o resuelve fuera de su directorio (AUD-007). El mensaje no dice
 * cuál de los tres casos fue: no se le da al cliente información del filesystem.
 */
public class ArchivoNoDisponibleException extends RuntimeException {
    public ArchivoNoDisponibleException() {
        super("El archivo no está disponible.");
    }
}
