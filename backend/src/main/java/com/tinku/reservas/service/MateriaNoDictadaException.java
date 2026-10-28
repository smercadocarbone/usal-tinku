package com.tinku.reservas.service;

/** La materia elegida al reservar no es una de las que dicta el Tutor (ADR-M3-07 §4bis). */
public class MateriaNoDictadaException extends RuntimeException {
    public MateriaNoDictadaException() {
        super("Este tutor no da esa materia. Elegí una de las que figuran en su perfil.");
    }
}
