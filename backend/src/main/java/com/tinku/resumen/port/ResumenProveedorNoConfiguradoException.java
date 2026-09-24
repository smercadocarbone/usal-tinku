package com.tinku.resumen.port;

/**
 * El proveedor de LLM NO esta activo: la property
 * {@code tinku.resumen.proveedor=gemini} (ADR-M6-03) no esta seteada, asi que el
 * bean de {@link ResumenProveedor} es el fail-closed — no hace NINGUNA llamada
 * de red y falla con un error claro. Es determinístico (config ausente), por eso
 * {@code ResumenService} no lo reintenta.
 */
public class ResumenProveedorNoConfiguradoException extends RuntimeException {

    public ResumenProveedorNoConfiguradoException() {
        super("No hay proveedor de LLM configurado para el resumen (ADR-M6-03): setea "
                + "tinku.resumen.proveedor=gemini (y GEMINI_API_KEY) para activar Gemini. "
                + "Nada sale hacia un modelo externo sin esa property.");
    }
}