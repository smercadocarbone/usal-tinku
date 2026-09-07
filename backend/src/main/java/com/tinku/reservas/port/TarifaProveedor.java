package com.tinku.reservas.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto hacia M5 (Pagos) para obtener la tarifa por sesión del Tutor
 * (Spec M5 US-6, FR-PAG-005/006). Ver Plan_M4 sección 1: {@code reservas.precio}
 * se congela al crear la Reserva con el valor que devuelve este puerto.
 *
 * M5 todavía no existe como módulo: el {@link TarifaProveedorStub} es la
 * implementación mientras tanto (misma mecánica que ReputacionSignalProvider en
 * M2-C y VerificadorReservasFuturas en M1). Cuando el Chunk M5 (configuración de
 * perfil de tarifa del Tutor) exista, la implementación real lo reemplaza.
 */
public interface TarifaProveedor {

    /** Tarifa por sesión vigente del Tutor. Puede cambiar con el tiempo — la
     * Reserva congela el valor al crearse (FR-PAG-013), no al reprogramarse. */
    BigDecimal tarifaPorSesion(UUID tutorId);
}