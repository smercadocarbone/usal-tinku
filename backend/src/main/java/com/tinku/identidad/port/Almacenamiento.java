package com.tinku.identidad.port;

/**
 * Puerto hacia el almacenamiento de archivos (credenciales de Tutor, US-4).
 *
 * <p>La URL que devuelve {@link #guardar} es una referencia interna: nunca se
 * expone al cliente (minimización, ADR-M1-03). El panel Admin (M8) ve el archivo
 * por {@code GET /api/admin/moderacion/credenciales/{id}/archivo}, que sirve los
 * bytes vía {@link #leer} (AUD-007).</p>
 *
 * Implementación actual: {@code AlmacenamientoLocal} (filesystem configurable
 * vía {@code tinku.almacenamiento.directorio}).
 */
public interface Almacenamiento {

    /** Persiste el archivo y devuelve su referencia interna. */
    String guardar(byte[] contenido, String nombreOriginal);

    /**
     * Lee un archivo guardado por {@link #guardar}. Lanza
     * {@link ArchivoNoDisponibleException} si la referencia no existe, no es de
     * este almacenamiento o resuelve fuera de él (path traversal).
     */
    byte[] leer(String referencia);

    /**
     * Borra un archivo guardado por {@link #guardar} (retención vencida, BR-KS-02).
     * Idempotente: si ya no existe no falla. Misma validación de referencia que {@link #leer}.
     */
    void borrar(String referencia);
}
