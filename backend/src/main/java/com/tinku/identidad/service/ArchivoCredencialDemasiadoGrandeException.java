package com.tinku.identidad.service;

import org.springframework.util.unit.DataSize;

/** AUD-007: la credencial supera {@code spring.servlet.multipart.max-file-size}. Se traduce a 413. */
public class ArchivoCredencialDemasiadoGrandeException extends RuntimeException {
    public ArchivoCredencialDemasiadoGrandeException(DataSize maximo) {
        super("La credencial supera el tamaño máximo de " + maximo.toMegabytes() + "MB.");
    }
}
