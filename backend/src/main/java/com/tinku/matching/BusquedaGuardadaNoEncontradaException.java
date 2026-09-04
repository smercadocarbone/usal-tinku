package com.tinku.matching;

/** Re-ejecutar o listar exige una búsqueda guardada que pertenezca al usuario
 * autenticado; cualquier otra referencia es 404 sin distinguir "existe pero no
 * es tuya" (no filtrar existencia). */
public class BusquedaGuardadaNoEncontradaException extends RuntimeException {

    public BusquedaGuardadaNoEncontradaException() {
        super("Búsqueda guardada no encontrada.");
    }
}