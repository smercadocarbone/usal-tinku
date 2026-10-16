package com.tinku.aula;

/**
 * «Finalizar» una clase en la que no entraron los dos: no hay clase que cerrar. La ausencia la
 * decide el no-show (T+10) y el cierre, el corte automático. Se traduce a 422.
 */
public class ClaseNoEmpezadaException extends RuntimeException {
    public ClaseNoEmpezadaException() {
        super("La clase todavía no empezó: se puede finalizar cuando hayan entrado los dos.");
    }
}
