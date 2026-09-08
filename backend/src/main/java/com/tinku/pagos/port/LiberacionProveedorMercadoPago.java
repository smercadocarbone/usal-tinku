package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.service.MercadoPagoNoDisponibleException;
import org.springframework.stereotype.Component;

/**
 * Implementación REAL de {@link LiberacionProveedor} (Chunk M5-C). El modelo
 * elegido en M5-A — Checkout Pro con {@code marketplace_fee} (Split Payments
 * 1:1) — reparte automáticamente los importes entre vendedor y marketplace al
 * capturar: NO hay endpoint de "liberación" por pago que llamar (la retención
 * hasta 24hs queda como configuración de cuenta; validar con ADR-M5-01 en el
 * ambiente real). Lo que Tinku SÍ controla al vencerse {@code liberar_at} es
 * verificar contra el proveedor que el pago sigue {@code approved} (no revertido
 * ni cargado a contracargo) antes de registrar {@code liberado}.
 *
 * Fail-closed: si MercadoPago no responde (o el pago ya no está aprobado) lanza
 * la excepción con que el job de Chunk M5-C dispara el backoff de FR-PAG-007 —
 * jamás se marca {@code liberado} un pago que el provider no confirma.
 */
@Component
public class LiberacionProveedorMercadoPago implements LiberacionProveedor {

    private final MercadoPagoClient mercadopago;

    public LiberacionProveedorMercadoPago(MercadoPagoClient mercadopago) {
        this.mercadopago = mercadopago;
    }

    @Override
    public void liberarAlTutor(Transaccion transaccion) {
        MercadoPagoClient.PagoMercadoPago pago = mercadopago.getPago(transaccion.getMpPaymentId());
        if (!pago.aprobado()) {
            // Revertido / contracargo: no es liberable; va al backoff.
            throw new MercadoPagoNoDisponibleException();
        }
    }
}