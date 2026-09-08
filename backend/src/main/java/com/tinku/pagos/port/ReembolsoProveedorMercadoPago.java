package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;
import org.springframework.stereotype.Component;

/**
 * Reembolso TOTAL al Estudiante vía la API de MercadoPago (T-M5-07, Plan M5
 * §3.3): {@code POST /v1/payments/{id}/refunds} con el body vacío, lo que hace
 * que MP reintegre además su propia comisión — costo real cero para Tinku
 * (FR-PAG-009). Nunca reembolsa parciales.
 *
 * <p>Fail-closed: si MercadoPago no responde (o la operación falla) lanza
 * {@code MercadoPagoNoDisponibleException} y la transacción del publicador se
 * aborta — jamás se registra un {@code reembolsado} sin haber reembolsado.
 * Una pareja con firma webhook válida que abusa del contenido no puede
 * fabricar un "reembolsado": el guard de monto del escrow corre antes que la
 * operación en la SAME transacción.
 */
@Component
public class ReembolsoProveedorMercadoPago implements ReembolsoProveedor {

    private final MercadoPagoClient mercadopago;

    public ReembolsoProveedorMercadoPago(MercadoPagoClient mercadopago) {
        this.mercadopago = mercadopago;
    }

    @Override
    public void reembolsarTotal(Transaccion transaccion) {
        mercadopago.reembolsarPago(transaccion.getMpPaymentId());
    }
}