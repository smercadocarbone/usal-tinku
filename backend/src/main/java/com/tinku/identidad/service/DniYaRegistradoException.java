package com.tinku.identidad.service;

/** Ver FR-ID-018: no exponer de quién es el DNI, solo que ya existe. */
public class DniYaRegistradoException extends RuntimeException {
    public DniYaRegistradoException() {
        super("Ya existe una cuenta registrada con ese DNI.");
    }
}
