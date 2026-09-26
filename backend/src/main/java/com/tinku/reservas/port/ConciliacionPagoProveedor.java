package com.tinku.reservas.port;

import java.util.UUID;

/**
 * Puerto hacia M5 (R2): antes de vencer una Reserva {@code pendiente_pago}, M5 pregunta a
 * MercadoPago si ya hay un pago aprobado y, si lo hay, la confirma. Mejor esfuerzo: si
 * MercadoPago no responde, no lanza y la Reserva vence igual (el barrido de conciliación
 * reembolsa después un pago que llegue tarde).
 *
 * Implementación real: {@code com.tinku.pagos.service.ConciliacionPagosService}.
 */
public interface ConciliacionPagoProveedor {

    void conciliarAntesDeVencer(UUID reservaId);
}
