package com.tinku.resumen.port;

/**
 * El ADR del proveedor de LLM (GPT-4o vs Gemini 2.0 Flash, T-FIN-03) esta
 * PENDIENTE en la Constitucion. Mientras no se cierre, el bean de
 * {@link ResumenProveedor} es este: fail-closed, no hace NINGUNA llamada de red
 * y falla con un error claro. Cuando se elija proveedor, esta clase se
 * reemplaza por la implementacion real (bean condicional) sin tocar el contrato.
 */
public class ResumenProveedorNoConfiguradoException extends RuntimeException {

    public ResumenProveedorNoConfiguradoException() {
        super("No hay proveedor de LLM configurado para el resumen (ADR T-FIN-03 pendiente). "
                + "Nada sale hacia un modelo externo: no se puede generar el resumen hasta "
                + "resolver el ADR en la Constitucion.");
    }
}