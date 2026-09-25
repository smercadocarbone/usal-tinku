package com.tinku.identidad.port;

import java.util.UUID;

/**
 * PT10 (T02): el Tutor perdió la habilitación para menores (CAP vencido o revocado) →
 * se cancelan sus reservas FUTURAS con un menor como beneficiario, con reembolso total y
 * aviso al Adulto Responsable. Las de adultos no se tocan. Lo implementa M4.
 */
public interface CancelacionReservasConMenores {

    /** @return cuántas reservas canceló. */
    int cancelarFuturasConMenores(UUID tutorId);
}
