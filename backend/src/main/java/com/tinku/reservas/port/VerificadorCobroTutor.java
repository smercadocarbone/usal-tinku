package com.tinku.reservas.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto hacia M5 (ADR-M5-02): con el modelo de OAuth por Tutor activo, un Tutor sin su cuenta de
 * MercadoPago conectada no puede cobrar, así que no se le reserva ni aparece en el matching. Sin
 * OAuth configurado, todos pueden. Implementación: {@code com.tinku.pagos.service.CuentasMpService}.
 */
public interface VerificadorCobroTutor {

    boolean puedeCobrar(UUID tutorId);

    Set<UUID> quienesPuedenCobrar(Collection<UUID> tutores);
}
