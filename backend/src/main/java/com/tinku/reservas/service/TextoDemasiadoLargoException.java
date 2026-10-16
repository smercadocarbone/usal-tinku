package com.tinku.reservas.service;

/** Pedido previo o nota de clase que pasa el tope de caracteres (FR-RES-026/027): se rechaza, no se corta. */
public class TextoDemasiadoLargoException extends RuntimeException {

    public TextoDemasiadoLargoException(int maximo) {
        super("El texto puede tener hasta " + maximo + " caracteres.");
    }

    /** Cuenta caracteres como los ve una persona (un emoji es uno), no unidades UTF-16. */
    static int largo(String texto) {
        return texto.codePointCount(0, texto.length());
    }

    /** Corta a {@code maximo} caracteres sin partir un emoji (el filtro puede alargar el texto). */
    static String cortar(String texto, int maximo) {
        if (largo(texto) <= maximo) {
            return texto;
        }
        return texto.substring(0, texto.offsetByCodePoints(0, maximo));
    }
}
