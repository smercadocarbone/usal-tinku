package com.tinku.identidad.port;

/**
 * Puerto hacia el almacenamiento de archivos (credenciales de Tutor, US-4).
 * Devuelve una URL usable para que el panel Admin (M8) la abra y revise.
 *
 * Implementación actual: {@code AlmacenamientoLocal} (filesystem configurable
 * vía {@code tinku.almacenamiento.directorio}).
 */
@FunctionalInterface
public interface Almacenamiento {

    /** Persiste el archivo y devuelve su URL accesible. */
    String guardar(byte[] contenido, String nombreOriginal);
}
