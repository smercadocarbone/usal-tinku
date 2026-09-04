package com.tinku.identidad.port;

/**
 * Puerto hacia el almacenamiento de archivos (credenciales de Tutor, US-4).
 * Devuelve una URL usable para que el panel Admin (M8) la abra y revise.
 *
 * Hasta que exista infraestructura de storage, el
 * {@code StubAlmacenamiento} no persiste el archivo y devuelve una URL
 * derivada del nombre — solo para no bloquear el flujo (ver
 * NOTAS_VERIFICACION.md de M1-E).
 */
@FunctionalInterface
public interface Almacenamiento {

    /** Persiste el archivo y devuelve su URL accesible. */
    String guardar(byte[] contenido, String nombreOriginal);
}
