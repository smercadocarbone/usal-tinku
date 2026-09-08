package com.tinku.pagos.service;

import com.tinku.pagos.evento.DenunciaRegistradaEvent;
import com.tinku.pagos.evento.SesionFinalizadaEvent;
import com.tinku.pagos.evento.SesionInterrumpidaEvent;
import com.tinku.pagos.evento.SesionKillswitchAdultosEvent;
import com.tinku.pagos.evento.SesionKillswitchMenorEvent;
import com.tinku.pagos.evento.SesionNoShowDobleEvent;
import com.tinku.pagos.evento.SesionNoShowEstudianteEvent;
import com.tinku.pagos.evento.SesionNoShowTutorEvent;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PagoMercadoPago;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.evento.ReservaCanceladaEvent;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Escrow de M5 (US-1/US-3, FR-PAG-001/002/004/009/012). Dos responsabilidades
 * del Chunk M5-B:
 *
 * <ol>
 *   <li><b>Webhook (T-M5-03):</b> {@link #procesarPagoAprobado} reacciona a una
 *       notificación de MercadoPago YA firmada por el controller. Reconciliación
 *       defensiva: consulta el pago real (GET /v1/payments/{id}) y solo si está
 *       {@code approved} y el monto coincide con el precio congelado crea la
 *       {@code Transaccion} en {@code retenido_escrow} y confirma la Reserva.</li>
 *   <li><b>Listeners (T-M5-04, completados en M5-C):</b> uno por evento entrante
 *       de la tabla del Plan M5 §2 — {@code sesion.finalizada} fija {@code liberar_at}
 *       (fin + 24hs) y agenda el job de liberación (Chunk M5-C); los de
 *       reembolso pasan por el port (real desde M5-D) y {@code denuncia.registrada}
 *       pausa el escrow; ambos salen de la ventana cancelando el job.</li>
 *   <li><b>M5-D:</b> {@code reserva.cancelada} resuelve la asimetría de FR-RES-008
 *       (≥24hs o cancela el Tutor → reembolso total; <24hs y cancela quien pagó →
 *       liberación al Tutor) y el webhook reembolsa los pagos tardíos (una
 *       notificación aprobada que llega cuando la Reserva ya salió de
 *       {@code pendiente_pago} — dinero cobrado sin sesión que se devuelve).</li>
 * </ol>
 *
 * Transiciones solo desde {@code retenido_escrow} (idempotente; un evento
 * reemitido sobre una transacción ya resuelta no produce nada). Los listeners
 * corren en la transacción del publicador: si el puerto falla, se aborta
 * (fail-closed) — jamás registrar {@code liberado}/{@code reembolsado} sin
 * ejecutar la operación contra MercadoPago.
 */
@Service
public class EscrowService {

    private static final Logger LOG = LoggerFactory.getLogger(EscrowService.class);

    /** FR-PAG-002: liberación al Tutor 24hs después de finalizada la Sesión. */
    static final Duration VENTANA_LIBERACION = Duration.ofHours(24);

    /** FR-RES-008/016: ventana de cancelación sin penalidad — ≥24hs al horario. */
    static final Duration VENTANA_CANCELACION = Duration.ofHours(24);

    private final TransaccionRepository transaccionRepo;
    private final ReservaRepository reservaRepo;
    private final ReservaService reservaService;
    private final MercadoPagoClient mercadopago;
    private final LiberacionEscrowService liberacionEscrow;
    private final ReembolsoProveedor reembolso;
    private final ComisionPlataforma comision;

    public EscrowService(TransaccionRepository transaccionRepo,
                         ReservaRepository reservaRepo,
                         ReservaService reservaService,
                         MercadoPagoClient mercadopago,
                         LiberacionEscrowService liberacionEscrow,
                         ReembolsoProveedor reembolso,
                         ComisionPlataforma comision) {
        this.transaccionRepo = transaccionRepo;
        this.reservaRepo = reservaRepo;
        this.reservaService = reservaService;
        this.mercadopago = mercadopago;
        this.liberacionEscrow = liberacionEscrow;
        this.reembolso = reembolso;
        this.comision = comision;
    }

    // ------------------------------------------------------ webhook (T-M5-03)

    /**
     * Procesa una notificación de pago firmada y válida. Idempotente: si ya
     * existe una {@code Transaccion} para ese {@code mpPaymentId}, no hace nada
     * (MP reintenta los no-2xx — nunca duplicar el escrow). Los ack's sin efecto
     * (pago no aprobado, external_reference no atribuible) responden 2xx igual
     * que LiveKit: el provider no debe reintentar en loop. Un pago aprobado que
     * llega cuando la Reserva YA no está {@code pendiente_pago} (timeout o
     * cancelación posterior al cobro, Chunk M5-D) se reembolsa en total: el
     * dinero se cobró pero la sesión ya no va a existir.
     */
    @Transactional
    public void procesarPagoAprobado(String mpPaymentId) {
        if (transaccionRepo.findByMpPaymentId(mpPaymentId).isPresent()) {
            return;
        }
        PagoMercadoPago pago = mercadopago.getPago(mpPaymentId);
        if (!pago.aprobado()) {
            return;
        }
        Reserva reserva = reservaPorExternalReference(pago.externalReference());
        if (reserva == null) {
            // external_reference no corresponde a ninguna Reserva de Tinku: no
            // reembolsar automáticamente un pago que no podemos atribuir.
            LOG.warn("Pago aprobado con external_reference no atribuible, se ignora: {} -> {}",
                    mpPaymentId, pago.externalReference());
            return;
        }
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            reembolsarPagoTardio(pago, reserva);
            return;
        }
        // Fail-closed: monto pagado ≠ precio congelado → no se confirma.
        if (pago.monto() == null || pago.monto().compareTo(reserva.getPrecio()) != 0) {
            throw new PagoInconsistenteException(reserva.getId(), mpPaymentId);
        }
        Transaccion transaccion = new Transaccion();
        transaccion.setReservaId(reserva.getId());
        transaccion.setMpPaymentId(mpPaymentId);
        transaccion.setMontoBruto(reserva.getPrecio());
        transaccion.setComisionPlataforma(comision.calcular(reserva.getPrecio()));
        transaccionRepo.save(transaccion);
        // Transición pendiente_pago → confirmada + ReservaConfirmadaEvent (M3
        // crea y agenda la Sesión). Idempotente; corre dentro de esta transacción.
        reservaService.confirmarPagoSimulado(reserva.getId());
    }

    /** La {@code external_reference} de la preferencia ES el id de la Reserva
     * (M5-A): es la clave de reconciliación del webhook. Desconocida → null. */
    private Reserva reservaPorExternalReference(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return null;
        }
        try {
            return reservaRepo.findById(UUID.fromString(externalReference)).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------ listeners (T-M5-04)

    /** {@code sesion.finalizada} (FR-PAG-002): inicia la cuenta de 24hs y agenda
     * el job de liberación (Chunk M5-C, T-M5-05). */
    @EventListener
    @Transactional
    public void onSesionFinalizada(SesionFinalizadaEvent evento) {
        transaccionRepo.findByReservaId(evento.getReservaId()).ifPresent(t -> {
            if (t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW) {
                Instant liberarAt = evento.getTimestampFin().plus(VENTANA_LIBERACION);
                t.setLiberarAt(liberarAt);
                transaccionRepo.save(t);
                liberacionEscrow.programarLiberacion(t.getId(), liberarAt);
            }
        });
    }

    /** {@code sesion.interrumpida} (FR-AULA-005): reembolso total inmediato. */
    @EventListener
    @Transactional
    public void onSesionInterrumpida(SesionInterrumpidaEvent evento) {
        reembolsarSiRetenida(evento.getReservaId());
    }

    /** {@code sesion.no_show_estudiante} (FR-RES-005): se cobra y se libera al
     * Tutor de inmediato (no espera las 24hs — el no-show ya confirma la ausencia).
     * Va por el mismo camino resiliente que el job (Chunk M5-C): si la liberación
     * falla, entra el backoff de FR-PAG-007 en lugar de fallar la transacción. */
    @EventListener
    @Transactional
    public void onSesionNoShowEstudiante(SesionNoShowEstudianteEvent evento) {
        transaccionRepo.findByReservaId(evento.getReservaId()).ifPresent(t -> {
            if (t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW) {
                liberacionEscrow.ejecutarLiberacion(t.getId());
            }
        });
    }

    /** {@code sesion.no_show_tutor} (FR-RES-005): reembolso total. */
    @EventListener
    @Transactional
    public void onSesionNoShowTutor(SesionNoShowTutorEvent evento) {
        reembolsarSiRetenida(evento.getReservaId());
    }

    /** {@code sesion.no_show_doble} (FR-RES-009): reembolso total, sin liberar nada. */
    @EventListener
    @Transactional
    public void onSesionNoShowDoble(SesionNoShowDobleEvent evento) {
        reembolsarSiRetenida(evento.getReservaId());
    }

    /** {@code sesion.killswitch_menor} (FR-PAG-009): reembolso total. */
    @EventListener
    @Transactional
    public void onSesionKillswitchMenor(SesionKillswitchMenorEvent evento) {
        reembolsarSiRetenida(evento.getReservaId());
    }

    /** {@code sesion.killswitch_adultos} (FR-PAG-012): reembolso total INCLUSO si
     * el detectado es el propio pagador — {@code detectadoId} se ignora a
     * propósito (ver javadoc del evento). */
    @EventListener
    @Transactional
    public void onSesionKillswitchAdultos(SesionKillswitchAdultosEvent evento) {
        reembolsarSiRetenida(evento.getReservaId());
    }

    /** {@code denuncia.registrada} (Spec_M5 §2): pausa el escrow hasta la
     * resolución y cancela la ventana de liberación si ya estaba programada. La
     * reanudación (denuncia.resuelta) queda pendiente de M9-D + M5-C/M5-D. */
    @EventListener
    @Transactional
    public void onDenunciaRegistrada(DenunciaRegistradaEvent evento) {
        transaccionRepo.findByReservaId(evento.getReservaId()).ifPresent(t -> {
            if (t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW) {
                t.setEstado(EstadoTransaccion.PAUSADO_DENUNCIA);
                t.setLiberarAt(null);
                transaccionRepo.save(t);
                liberacionEscrow.cancelarLiberacion(t.getId());
            }
        });
    }

    private void reembolsarSiRetenida(UUID reservaId) {
        transaccionRepo.findByReservaId(reservaId).ifPresent(t -> {
            if (t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW) {
                // Única vía de reembolso (Plan §3.3, FR-PAG-009): el port real de
                // M5-D llama a MP con body vacío. Nunca reimplementado acá.
                reembolso.reembolsarTotal(t);
                t.setEstado(EstadoTransaccion.REEMBOLSADO);
                t.setLiberarAt(null);
                transaccionRepo.save(t);
                // No liberar después de haber reembolsado (Chunk M5-C).
                liberacionEscrow.cancelarLiberacion(t.getId());
            }
        });
    }

    // ------------------------------------------------------ M5-D (T-M5-07)

    /** Cancelación tardía (US-7, FR-RES-008/016): asimetría según quién cancela.
     * Con ≥24hs al horario, o si cancela el Tutor → reembolso total. Con <24hs y
     * cancela quien pagó → se libera el escrow al Tutor (no es una transacción
     * nueva, es el mismo dinero ya retenido que simplemente se libera). */
    @EventListener
    @Transactional
    public void onReservaCancelada(ReservaCanceladaEvent evento) {
        transaccionRepo.findByReservaId(evento.getReservaId())
                .filter(t -> t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW)
                .ifPresent(t -> {
                    Reserva reserva = reservaRepo.findById(evento.getReservaId()).orElse(null);
                    if (reserva == null) {
                        return; // invariable: no hay Reserva sin escrow confirmado
                    }
                    boolean conMargen = !Instant.now().plus(VENTANA_CANCELACION)
                            .isAfter(reserva.getHorario());
                    boolean canceloElPagador = reserva.getPagador() != null
                            && reserva.getPagador().getId().equals(evento.getCanceladaPorUsuarioId());
                    if (conMargen || !canceloElPagador) {
                        reembolsarSiRetenida(evento.getReservaId());
                    } else {
                        // Liberación inmediata, mismo camino resiliente que el
                        // no-show del Estudiante (backoff de FR-PAG-007 si falla).
                        liberacionEscrow.ejecutarLiberacion(t.getId());
                    }
                });
    }

    /** Pago aprobado que llega después de que la Reserva salió de {@code
     * pendiente_pago} (timeout o cancelación posterior al cobro): el dinero se
     * cobró pero la sesión no se va a dar → reembolso total. Si la Reserva nunca
     * tuvo escrow, se registra una {@code Transaccion} {@code reembolsado} como
     * ancla (idempotencia del reenvío del webhook + auditoría); si ya tuvo escrow
     * (Reserva confirmada que recién canceló), NO se crea una fila duplicada —
     * se preserva la unicidad de {@code findByReservaId}. */
    private void reembolsarPagoTardio(PagoMercadoPago pago, Reserva reserva) {
        Transaccion tardia = new Transaccion();
        tardia.setReservaId(reserva.getId());
        tardia.setMpPaymentId(pago.mpPaymentId());
        tardia.setMontoBruto(pago.monto() != null ? pago.monto() : reserva.getPrecio());
        tardia.setComisionPlataforma(BigDecimal.ZERO);
        if (!transaccionRepo.existsByReservaId(reserva.getId())) {
            tardia.setEstado(EstadoTransaccion.REEMBOLSADO);
            transaccionRepo.save(tardia);
        }
        reembolso.reembolsarTotal(tardia);
    }
}