package com.tinku.resumen.port;

import org.springframework.stereotype.Component;

/**
 * Bean por defecto de {@link ResumenProveedor} — fail-closed (T-M6-05): sin
 * ADR resuelto (T-FIN-03) no existe ningun proveedor, asi que este bean rechaza
 * toda generacion con {@link ResumenProveedorNoConfiguradoException}. Cuando se
 * elija proveedor, se agrega la implementacion real y se reemplaza este bean
 * (mismo enfoque fail-closed que se usó en M5 hasta conectar MercadoPago).
 */
@Component
public class ResumenProveedorFailClosed implements ResumenProveedor {

    @Override
    public ResumenResultado generarResumen(ResumenRequest request) {
        throw new ResumenProveedorNoConfiguradoException();
    }
}