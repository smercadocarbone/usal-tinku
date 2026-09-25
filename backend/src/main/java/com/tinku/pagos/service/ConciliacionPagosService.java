package com.tinku.pagos.service;

import com.tinku.pagos.model.PreferenciaMp;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PagoMercadoPago;
import com.tinku.pagos.repository.PreferenciaMpRepository;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.port.ConciliacionPagoProveedor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Conciliación de pagos con MercadoPago (R2, `docs/spikes/DISENO-soluciones-revision-por-rol.md`
 * §5). Cubre el caso en que el comprador paga, cierra la pestaña y el webhook se pierde: sin esto
 * la Reserva vencía y la plata quedaba cobrada sin confirmar ni reembolsar.
 *
 * <p>No agrega lógica de dinero: por cada pago {@code approved} de la Reserva llama a
 * {@link EscrowService#procesarPagoAprobado}, que ya es idempotente, valida el monto y reembolsa
 * un pago tardío. Cada pago corre en su propia transacción (la de {@code procesarPagoAprobado});
 * este servicio no abre una, para que un pago que falla no revierta a los demás.</p>
 *
 * <p>Plazos (Tabla de Tiempos): barrido cada 5 min durante 48 hs desde la preferencia.</p>
 */
@Service
public class ConciliacionPagosService implements ConciliacionPagoProveedor {

    /** Tabla de Tiempos: ventana del barrido de conciliación. */
    public static final Duration VENTANA_CONCILIACION = Duration.ofHours(48);
    /** Margen para no pisar al comprador que está volviendo de MercadoPago en este momento. */
    static final Duration MARGEN_MINIMO = Duration.ofMinutes(2);

    private static final Logger LOG = LoggerFactory.getLogger(ConciliacionPagosService.class);

    private final PreferenciaMpRepository preferenciaRepo;
    private final TransaccionRepository transaccionRepo;
    private final MercadoPagoClient mercadopago;
    private final EscrowService escrow;
    private final PasarelaService pasarela;
    private final AlertaSoporteProveedor alertaSoporte;

    public ConciliacionPagosService(PreferenciaMpRepository preferenciaRepo,
                                    TransaccionRepository transaccionRepo,
                                    MercadoPagoClient mercadopago,
                                    EscrowService escrow,
                                    PasarelaService pasarela,
                                    AlertaSoporteProveedor alertaSoporte) {
        this.preferenciaRepo = preferenciaRepo;
        this.transaccionRepo = transaccionRepo;
        this.mercadopago = mercadopago;
        this.escrow = escrow;
        this.pasarela = pasarela;
        this.alertaSoporte = alertaSoporte;
    }

    /** La registra {@code PagoService.generarPreferencia}; una sola por Reserva (la última gana). */
    public void registrarPreferencia(UUID reservaId, String preferenceId) {
        PreferenciaMp preferencia = preferenciaRepo.findById(reservaId).orElseGet(() -> {
            PreferenciaMp nueva = new PreferenciaMp();
            nueva.setReservaId(reservaId);
            return nueva;
        });
        preferencia.setPreferenceId(preferenceId);
        preferenciaRepo.save(preferencia);
    }

    @Override
    public void conciliarAntesDeVencer(UUID reservaId) {
        if (preferenciaRepo.existsById(reservaId)) {
            conciliar(reservaId);
        }
    }

    /** Barrido (lo llama {@code ConciliacionPagosJob}). Devuelve cuántas Reservas quedaron conciliadas. */
    public int barrer() {
        if (!pasarela.estaHabilitada()) {
            return 0; // modo Bypass: no hay pagos reales que buscar
        }
        Instant ahora = Instant.now();
        List<UUID> pendientes = preferenciaRepo.pendientesDeConciliar(
                ahora.minus(VENTANA_CONCILIACION), ahora.minus(MARGEN_MINIMO));
        int conciliadas = 0;
        for (UUID reservaId : pendientes) {
            if (conciliar(reservaId)) {
                conciliadas++;
            }
        }
        return conciliadas;
    }

    /**
     * Busca los pagos de la Reserva y procesa los aprobados. Nunca lanza: si MercadoPago no
     * responde, queda para el próximo barrido. Devuelve {@code true} si la Reserva ya tiene su
     * Transaccion (confirmada o reembolsada) y deja de mirarse.
     */
    public boolean conciliar(UUID reservaId) {
        List<PagoMercadoPago> pagos;
        try {
            pagos = mercadopago.buscarPagosPorReferencia(reservaId.toString());
        } catch (RuntimeException e) {
            LOG.warn("Conciliación: MercadoPago no respondió para la reserva {} — se reintenta en el "
                    + "próximo barrido", reservaId, e);
            sumarIntento(reservaId);
            return false;
        }
        for (PagoMercadoPago pago : pagos) {
            // Solo los pagos de ESTA reserva (la búsqueda filtra, pero no se confía a ciegas).
            if (!pago.aprobado() || !reservaId.toString().equals(pago.externalReference())) {
                continue;
            }
            try {
                escrow.procesarPagoAprobado(pago.mpPaymentId());
            } catch (PagoInconsistenteException e) {
                alertarUnaVez(reservaId, pago.mpPaymentId(), "el monto pagado no coincide con el de la reserva");
            } catch (RuntimeException e) {
                LOG.warn("Conciliación: no se pudo procesar el pago {} de la reserva {}",
                        pago.mpPaymentId(), reservaId, e);
            }
        }
        boolean resuelta = transaccionRepo.existsByReservaId(reservaId);
        preferenciaRepo.findById(reservaId).ifPresent(p -> {
            p.setIntentosConciliacion(p.getIntentosConciliacion() + 1);
            if (resuelta) {
                p.setConciliadoAt(Instant.now());
            }
            preferenciaRepo.save(p);
        });
        return resuelta;
    }

    private void sumarIntento(UUID reservaId) {
        preferenciaRepo.findById(reservaId).ifPresent(p -> {
            p.setIntentosConciliacion(p.getIntentosConciliacion() + 1);
            preferenciaRepo.save(p);
        });
    }

    private void alertarUnaVez(UUID reservaId, String mpPaymentId, String motivo) {
        preferenciaRepo.findById(reservaId).ifPresent(p -> {
            if (p.getAlertadoAt() != null) {
                return;
            }
            p.setAlertadoAt(Instant.now());
            preferenciaRepo.save(p);
            alertaSoporte.notificarPagoSinConciliar(reservaId, mpPaymentId, motivo);
        });
    }
}
