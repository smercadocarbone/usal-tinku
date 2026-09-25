package com.tinku.reservas.port;

import java.util.UUID;

/**
 * FR-ID-026: ¿el Tutor tiene un CAP aprobado y vigente? Lo implementa M1 (el CAP es suyo).
 * Puerto para no sumar imports cruzados (AUD-019), mismo patrón que VerificadorSancionVigente.
 */
public interface VerificadorHabilitacionMenores {

    boolean habilitadoParaMenores(UUID tutorId);
}
