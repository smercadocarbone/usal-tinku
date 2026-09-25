package com.tinku.pagos.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puente hacia MercadoPago (T-M5-02). El dominio de pagos habla con conceptos
 * Tinku (reservaId, monto, comision) y la implementacion concreta traduce a el
 * contrato JSON del provider — asi la regla BR-PAG-01 (comision = 27%) nunca
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

    /**
     * Reembolso TOTAL (POST /v1/payments/{id}/refunds con body VACÍO, Plan M5
     * §3.3, T-M5-07): MercadoPago reintegra también su propia comisión (costo
     * real cero para Tinku, FR-PAG-009). Es la ÚNICA llamada de reembolso que
     * usa el flujo automático del escrow — los reembolsos parciales son flujo
     * manual de M8 (T-M5-08) y jamás pasan por acá ni por {@code
     * ReembolsoProveedor}.
     */
    void reembolsarPago(String mpPaymentId);

    /**
     * Reembolso PARCIAL (POST /v1/payments/{id}/refunds con {@code amount}
     * explícito, Plan M5 §3.3, FR-PAG-010) — flujo MANUAL de disputa ejecutado
     * desde M8. El monto lo decide Soporte; la diferencia de comisión la
     * absorbe Tinku. Solo lo invoca {@code ReembolsoParcialProveedor}, nunca un
     * listener/job automático (T-M5-08).
     */
    void reembolsarPagoParcial(String mpPaymentId, BigDecimal monto);

    /**
     * Pagos de MercadoPago con esa {@code external_reference} (= id de la Reserva), vía
     * {@code GET /v1/payments/search} (R2). Es la conciliación: no depende de que vuelva el
     * navegador ni de que llegue el webhook.
     */
    List<PagoMercadoPago> buscarPagosPorReferencia(String externalReference);

    /**
     * {@code expiraAt} (R2): la preferencia no acepta pagos después (el timeout de la Reserva
     * sin pagar, Tabla de Tiempos). {@code null} = sin vencimiento.
     */
    record PreferenciaRequest(UUID reservaId, BigDecimal montoBruto,
                              BigDecimal comisionPlataforma, String descripcion, Instant expiraAt) {

        public PreferenciaRequest(UUID reservaId, BigDecimal montoBruto,
                                  BigDecimal comisionPlataforma, String descripcion) {
            this(reservaId, montoBruto, comisionPlataforma, descripcion, null);
        }
    }

    /** {@code bypass=true} cuando la preferencia se generó en modo Bypass (V22):
     * no hay {@code initPoint} porque nunca se le pidió un pago a MercadoPago —
     * el frontend lo muestra como pago simulado. */
    record PreferenciaPago(String preferenceId, String initPoint, boolean bypass) {
    }

    record PagoMercadoPago(String mpPaymentId, String status, String externalReference,
                           BigDecimal monto) {

        /** Estado {@code approved} de la API de MP. */
        public boolean aprobado() {
            return "approved".equals(status);
        }
    }
}