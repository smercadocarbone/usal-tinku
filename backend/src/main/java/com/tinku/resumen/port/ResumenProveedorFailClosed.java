package com.tinku.resumen.port;

/**
 * {@link ResumenProveedor} fail-closed (T-M6-05): lo elige
 * {@code ResumenProveedorConfig} cuando {@code LLM_PROVEEDOR} no es
 * {@code gpt-4o} (ADR-M6-03). Rechaza toda generacion con
 * {@link ResumenProveedorNoConfiguradoException} sin ninguna llamada de red.
 */
public class ResumenProveedorFailClosed implements ResumenProveedor {

    @Override
    public ResumenResultado generarResumen(ResumenRequest request) {
        throw new ResumenProveedorNoConfiguradoException();
    }
}