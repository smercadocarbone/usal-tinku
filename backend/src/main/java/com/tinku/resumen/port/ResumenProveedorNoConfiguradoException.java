package com.tinku.resumen.port;

/**
 * No hay proveedor de LLM activo: falta {@code LLM_PROVEEDOR=gpt-4o} o su
 * {@code LLM_API_KEY} (ADR-M6-03). No se hace NINGUNA llamada de red.
 */
public class ResumenProveedorNoConfiguradoException extends RuntimeException {

    public ResumenProveedorNoConfiguradoException() {
        super("No hay proveedor de LLM configurado para el resumen (ADR-M6-03, T-FIN-03): "
                + "hace falta LLM_PROVEEDOR=gpt-4o y LLM_API_KEY. Nada sale hacia un modelo "
                + "externo mientras tanto.");
    }
}