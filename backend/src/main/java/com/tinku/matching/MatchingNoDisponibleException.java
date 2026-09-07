package com.tinku.matching;

/**
 * El servicio Python de matching no está disponible (503, error de red o
 * timeout — ver {@link MatchingServiceClient#match}). El backend Java no
 * fabrica un ranking en este caso: le responde al cliente que la búsqueda no
 * está disponible, sin exponer detalle interno del proceso.
 */
public class MatchingNoDisponibleException extends RuntimeException {

    public MatchingNoDisponibleException() {
        super("El motor de búsqueda no está disponible en este momento. Intentá de nuevo en unos minutos.");
    }
}