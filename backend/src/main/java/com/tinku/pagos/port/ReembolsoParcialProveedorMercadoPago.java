package com.tinku.pagos.port;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Reembolso PARCIAL por disputa vía la API de MercadoPago (T-M5-08, Plan M5
 * §3.3, FR-PAG-010): {@code POST /v1/payments/{id}/refunds} con {@code amount}
 * explícito. La diferencia de comisión de gateway la absorbe Tinku, nunca el
 * Estudiante (FR-PAG-010).
 *
 * <p><b>Explicitamente MANUAL.</b> Este port separa el flujo de disputa (M8/M9)
 * del automático: los listeners/webhooks/jobs solo conocen
 * {@code ReembolsoProveedor.reembolsarTotal}, nunca este bean — imposible que
 * una regla automática haga un parcial por error (FR-PAG-009).</p>
 *
 * <p>La validación del {@code monto} (&gt; 0 y &lt; total cobrado) la hace M8
 * contra la Transacción antes de invocar — acá solo se ejecuta la operación.
 * Fail-closed: si MercadoPago no responde, {@code MercadoPagoNoDisponibleException}
 * aborta la operación del panel — jamás se reporta un parcial devuelto sin
 * haberlo ejecutado.</p>
 */
@Component
public class ReembolsoParcialProveedorMercadoPago implements ReembolsoParcialProveedor {

    private final MercadoPagoClient mercadopago;

    public ReembolsoParcialProveedorMercadoPago(MercadoPagoClient mercadopago) {
        this.mercadopago = mercadopago;
    }

    @Override
    public void reembolsarParcial(String mpPaymentId, BigDecimal monto) {
        mercadopago.reembolsarPagoParcial(mpPaymentId, monto);
    }
}