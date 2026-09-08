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

    /**
     * Consulta un pago (GET /v1/payments/{id}). Lo usa el webhook de M5-B
     * (T-M5-03) para reconciliar la notificación: verifica el estado real y el
     * monto contra la Reserva (fail-closed) en vez de confiar en el payload del
     * webhook — la pieza clave para que "el que llama es quien dice ser" no
     * alcance a fabricar una confirmación.
     */
    PagoMercadoPago getPago(String mpPaymentId);

    record PreferenciaRequest(UUID reservaId, BigDecimal montoBruto,
                              BigDecimal comisionPlataforma, String descripcion) {
    }

    record PreferenciaPago(String preferenceId, String initPoint) {
    }

    record PagoMercadoPago(String mpPaymentId, String status, String externalReference,
                           BigDecimal monto) {

        /** Estado {@code approved} de la API de MP. */
        public boolean aprobado() {
            return "approved".equals(status);
        }
    }
}