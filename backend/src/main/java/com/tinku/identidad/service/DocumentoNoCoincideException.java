package com.tinku.identidad.service;

/** El nombre/apellido/fecha/DNI declarado no coincide con lo extraído del documento. */
public class DocumentoNoCoincideException extends RuntimeException {
    public DocumentoNoCoincideException() {
        super("Los datos que ingresaste no coinciden con los de tu DNI. Revisá tu nombre, apellido, fecha de nacimiento y número de documento.");
    }
}
