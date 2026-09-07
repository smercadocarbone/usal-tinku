package com.tinku.identidad.service;

/**
 * FR-ID-016: un Adulto Responsable no puede desactivar esa capacidad
 * mientras tenga perfiles de menor a cargo.
 */
public class NoPuedeDesactivarAdultoResponsableException extends RuntimeException {
    public NoPuedeDesactivarAdultoResponsableException() {
        super("No podés desactivar 'Adulto Responsable' mientras tengas menores a cargo.");
    }
}
