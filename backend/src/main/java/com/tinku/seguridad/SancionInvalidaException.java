package com.tinku.seguridad;

/** Combinación de sanción inválida (422): p. ej. suspensión temporal sin
 * indicar los días, o días no válidos para el tipo elegido (Plan_M9 §1). */
public class SancionInvalidaException extends RuntimeException {
    public SancionInvalidaException(String detalle) {
        super("Sanción inválida: " + detalle);
    }
}