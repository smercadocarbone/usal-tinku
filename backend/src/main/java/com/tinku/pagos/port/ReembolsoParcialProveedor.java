package com.tinku.pagos.port;

import java.math.BigDecimal;

/**
 * Reembolso PARCIAL por disputa (Plan M5 §3.3, FR-PAG-010) — flujo MANUAL
 * ejecutado desde M8 (Soporte/Dirección Educativa) en el contexto de una
 * disputa donde se devuelve solo una parte del escrow.
 *
 * <p><b>Explicitamente separado del flujo automático (T-M5-08):</b> ninguna regla
 * automática de este módulo (webhook, listeners de sesion/denuncia, job de
 * liberación) invoca este port — el flujo automático solo conoce
 * {@code ReembolsoProveedor.reembolsarTotal}, para que sea imposible que una
 * regla automática termine haciendo un parcial por error. La diferencia de
 * comisión en un parcial la absorbe Tinku (FR-PAG-010).</p>
 *
 * <p><b>Contrato:</b> {@code monto} debe ser > 0 y menor que el total cobrado del
 * pago ({@code mpPaymentId}); la resolución de la disputa (quién, cuánto, motivo,
 * autorización) es responsabilidad de M8. Implementación real:
 * {@code com.tinku.pagos.port.ReembolsoParcialProveedorMercadoPago} — la
 * validación de monto y estado la hace el endpoint de M8 (422), no el puerto.</p>
 */
public interface ReembolsoParcialProveedor {

    void reembolsarParcial(String mpPaymentId, BigDecimal monto);
}