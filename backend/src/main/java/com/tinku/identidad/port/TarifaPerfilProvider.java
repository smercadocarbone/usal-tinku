package com.tinku.identidad.port;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto hacia M5 (Pagos) para mostrar el precio del Tutor en su perfil público
 * (UX-04 §2). A diferencia de {@code reservas.port.TarifaProveedor}, NO cae al
 * stub de desarrollo: el perfil muestra solo el precio que el Tutor configuró de
 * verdad; si no configuró ninguno, vacío ("a consultar").
 */
public interface TarifaPerfilProvider {

    Optional<BigDecimal> tarifaConfigurada(UUID tutorId);
}
