package com.tinku.reservas.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto hacia M5 (Pagos) para obtener la tarifa por hora del Tutor
 * (Spec M5 US-6, FR-PAG-005/006). Ver Plan_M4 sección 1: {@code reservas.precio}
 * se congela al crear la Reserva: {@code precioHora × duracionMinutos / 60} (D6).
 *
 * Implementación real: {@code com.tinku.pagos.service.TarifaProveedorTutor},
 * que lee {@code pagos.tarifas_tutor} (el Tutor la fija desde su perfil) con
 * fallback de dev a {@code tinku.reservas.tarifa-stub}.
 */
public interface TarifaProveedor {

    /** Tarifa por hora vigente del Tutor. Puede cambiar con el tiempo — la
     * Reserva congela el valor al crearse (FR-PAG-013), no al reprogramarse. */
    BigDecimal precioHora(UUID tutorId);
}