package com.tinku.resumen.port;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bean por defecto de {@link ResumenProveedor} — fail-closed (T-M6-05): sin la
 * property {@code tinku.resumen.proveedor=gemini} no hay proveedor activo
 * (ADR-M6-03), asi que este bean rechaza toda generacion con
 * {@link ResumenProveedorNoConfiguradoException}. Con el proveedor elegido, su
 * adapter {@link ResumenProveedorGemini} reemplaza este bean SOLO cuando la
 * property dice {@code gemini} (misma filosofia fail-closed que M5 hasta
 * conectar MercadoPago; tests y dev no llaman a Google).
 */
@Component
@ConditionalOnProperty(name = "tinku.resumen.proveedor", havingValue = "none",
        matchIfMissing = true)
public class ResumenProveedorFailClosed implements ResumenProveedor {

    @Override
    public ResumenResultado generarResumen(ResumenRequest request) {
        throw new ResumenProveedorNoConfiguradoException();
    }
}