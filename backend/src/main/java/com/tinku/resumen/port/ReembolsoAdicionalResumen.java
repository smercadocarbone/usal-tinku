package com.tinku.resumen.port;

import java.util.UUID;

/**
 * T09 (BR-PAG-11): si el resumen contratado termina fallido, se reembolsa SOLO el adicional.
 * Puerto de M6 implementado por M5 (mismo patrón que los puertos del CAP): M6 no conoce
 * MercadoPago ni las Transacciones. Idempotente: un segundo llamado no reembolsa dos veces.
 */
public interface ReembolsoAdicionalResumen {

    void reembolsarAdicional(UUID reservaId);
}
