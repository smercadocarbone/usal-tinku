package com.tinku.pagos.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puente hacia MercadoPago (T-M5-02). El dominio de pagos habla con conceptos
 * Tinku (reservaId, monto, comision) y la implementacion concreta traduce a el
 * contrato JSON del provider — asi la regla BR-PAG-01 (comision = 15%) nunca
 * depende del formato del tercero y los tests pueden reemplazar el cliente
 * real sin tocar la capa de servicio.
 */
public interface MercadoPagoClient {

    /**
     * Crea una preferencia de Checkout Pro y devuelve el link de pago
     * ({@code init_point}) para redirigir al comprador.
     */
    PreferenciaPago crearPreferencia(PreferenciaRequest request);

    record PreferenciaRequest(UUID reservaId, BigDecimal montoBruto,
                              BigDecimal comisionPlataforma, String descripcion) {
    }

    record PreferenciaPago(String preferenceId, String initPoint) {
    }
}