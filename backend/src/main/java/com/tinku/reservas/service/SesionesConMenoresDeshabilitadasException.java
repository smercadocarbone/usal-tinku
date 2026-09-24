package com.tinku.reservas.service;

/**
 * T-TES-10/DT7 — copy único del gate de menores a nivel negocio. Se traduce a
 * 409 por {@code ReservasExceptionHandler}: "Las clases para menores se
 * habilitan al finalizar el piloto."
 */
public class SesionesConMenoresDeshabilitadasException extends RuntimeException {
    public SesionesConMenoresDeshabilitadasException() {
        super("Las clases para menores se habilitan al finalizar el piloto.");
    }
}