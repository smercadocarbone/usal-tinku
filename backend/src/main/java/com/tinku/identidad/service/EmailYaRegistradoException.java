package com.tinku.identidad.service;

/** Análogo a {@link DniYaRegistradoException} para el cambio de email (FR-ID-018). */
public class EmailYaRegistradoException extends RuntimeException {
    public EmailYaRegistradoException() {
        super("Ya existe una cuenta registrada con ese email.");
    }
}
