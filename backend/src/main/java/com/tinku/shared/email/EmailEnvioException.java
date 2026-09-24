package com.tinku.shared.email;

/** El proveedor no aceptó el envío (red, 4xx/5xx): quien llama decide si reintenta. */
public class EmailEnvioException extends RuntimeException {
    public EmailEnvioException(String mensaje) {
        super(mensaje);
    }

    public EmailEnvioException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
