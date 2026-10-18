package com.tinku.reservas.service;

/**
 * T-TES-10/DT7 — copy único del gate de menores a nivel negocio. Se traduce a
 * 409 {@code MENORES_NO_HABILITADAS} por {@code ReservasExceptionHandler}. No
 * promete fecha: el encendido es una decisión del dueño (M3C-09).
 */
public class SesionesConMenoresDeshabilitadasException extends RuntimeException {
    public SesionesConMenoresDeshabilitadasException() {
        super("Las clases para menores todavía no están habilitadas.");
    }
}