package com.tinku.pagos.service;

import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.ReembolsoParcialProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.resumen.port.ReembolsoAdicionalResumen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * BR-PAG-11 (T09): reembolso parcial por el monto exacto del adicional de resumen. Única excepción
 * a los reembolsos totales de M5. No aplica si la Reserva ya se reembolsó entera, si no hubo
 * adicional o si ya se reembolsó. En Bypass no hay dinero real: solo se marca.
 * Si MercadoPago falla, queda sin marcar y se loguea en ERROR: el Admin financiero lo resuelve
 * por la cola de reembolsos parciales (FR-PAG-010).
 */
@Component
public class ReembolsoAdicionalResumenMercadoPago implements ReembolsoAdicionalResumen {

    private static final Logger LOG = LoggerFactory.getLogger(ReembolsoAdicionalResumenMercadoPago.class);

    private final TransaccionRepository transaccionRepo;
    private final ReembolsoParcialProveedor reembolsoParcial;

    public ReembolsoAdicionalResumenMercadoPago(TransaccionRepository transaccionRepo,
                                                ReembolsoParcialProveedor reembolsoParcial) {
        this.transaccionRepo = transaccionRepo;
        this.reembolsoParcial = reembolsoParcial;
    }

    @Override
    @Transactional
    public void reembolsarAdicional(UUID reservaId) {
        Transaccion t = transaccionRepo.findByReservaId(reservaId).orElse(null);
        if (t == null || t.getAdicionalReembolsadoAt() != null
                || t.getEstado() == EstadoTransaccion.REEMBOLSADO
                || t.getMontoAdicionalResumen() == null
                || t.getMontoAdicionalResumen().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (!t.isEnBypass()) {
            try {
                reembolsoParcial.reembolsarParcial(t.getMpPaymentId(), t.getMontoAdicionalResumen());
            } catch (RuntimeException e) {
                LOG.error("REEMBOLSO_ADICIONAL_FALLIDO reservaId={} monto={} — resolver por la cola "
                        + "de reembolsos parciales (FR-PAG-010).", reservaId, t.getMontoAdicionalResumen(), e);
                return;
            }
        }
        t.setAdicionalReembolsadoAt(Instant.now());
        transaccionRepo.save(t);
        LOG.info("REEMBOLSO_ADICIONAL reservaId={} monto={}", reservaId, t.getMontoAdicionalResumen());
    }
}
