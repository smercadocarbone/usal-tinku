package com.tinku.pagos.service;

import com.tinku.pagos.evento.AlertaResueltaEvent;
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
import com.tinku.reservas.model.PoliticaCancelacion;
import com.tinku.reservas.evento.DenunciaResueltaEvent;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservaService;
import com.tinku.shared.ResolucionDenuncia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

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
     *
     * <p>El guard de arriba es un check-then-act: no protege contra dos
     * reintentos de MP solapados que lo pasan antes de que el primero
     * commitee (AUD-010). La protección real es la unicidad de base de V24
     * ({@code uq_transacciones_mp_payment}, {@code uq_transacciones_reserva}):
     * si el {@code saveAndFlush} viola cualquiera de los dos índices, el otro
     * hilo ya ganó la carrera — se trata como no-op idempotente y NUNCA se
     * relanza (un 5xx hace que MP reintente en loop, el mismo problema que
     * describe el javadoc de {@link #reembolsarPagoTardio}).</p>
     *
     * <p>Dos pagos DISTINTOS para la misma Reserva (doble click en "Pagar" en dos
     * pestañas) no son un reintento: el perdedor tiene que reembolsarse. Por eso la
     * Reserva se lee con {@code FOR UPDATE} y el guard de {@code mpPaymentId} se repite
     * después del lock: el segundo webhook espera al primero, ve la Reserva ya
     * {@code confirmada} y va por {@link #reembolsarPagoTardio}. Sin el lock, ambos
     * veían {@code pendiente_pago} y el perdedor caía en el catch del índice único,
     * con su plata cobrada y nunca devuelta.</p>
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
        Reserva reserva = reservaBloqueadaPorExternalReference(pago.externalReference());
        if (reserva == null) {
            // external_reference no corresponde a ninguna Reserva de Tinku: no
            // reembolsar automáticamente un pago que no podemos atribuir.
            LOG.warn("Pago aprobado con external_reference no atribuible, se ignora: {} -> {}",
                    mpPaymentId, pago.externalReference());
            return;
        }
        // Re-chequeo bajo el lock: un reintento de ESTE mismo pago que esperó al
        // primero lo encuentra ya persistido (antes caía en el catch de abajo).
        if (transaccionRepo.findByMpPaymentId(mpPaymentId).isPresent()) {
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
        try {
            // flush inmediato: fuerza el INSERT ahora (no en el commit del
            // proxy transaccional) para poder capturar la violación de unicidad
            // DENTRO de este método y devolver 2xx igual.
            transaccionRepo.saveAndFlush(transaccion);
        } catch (DataIntegrityViolationException e) {
            LOG.info("Webhook de MP duplicado por reintentos solapados (AUD-010): "
                    + "mpPaymentId={} reservaId={} — el otro hilo ya creó el escrow, no-op idempotente.",
                    mpPaymentId, reserva.getId());
            // El INSERT falló: la transacción de Postgres quedó abortada. No
            // dejar que el proxy la commitee (fallaría igual) — se descarta.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return;
        }
        // Transición pendiente_pago → confirmada + ReservaConfirmadaEvent (M3
        // crea y agenda la Sesión). Idempotente; corre dentro de esta transacción.
        reservaService.confirmarPagoSimulado(reserva.getId());
    }

    /** La {@code external_reference} de la preferencia ES el id de la Reserva
     * (M5-A): es la clave de reconciliación del webhook. Desconocida → null. Se
     * lee con lock de fila (ver javadoc de {@link #procesarPagoAprobado}). */
    private Reserva reservaBloqueadaPorExternalReference(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return null;
        }
        try {
            return reservaRepo.findByIdParaActualizar(UUID.fromString(externalReference)).orElse(null);
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

    /** {@code sesion.killswitch_menor}: el corte es inmediato, la plata no
     * (ADR-M3-02, AUD-005). El escrow se pausa hasta que M9 resuelva la Alerta
     * ({@link #onAlertaResuelta}); antes se reembolsaba acá, sin revisión, y eso
     * premiaba disparar el kill-switch al final de una clase ya dada. FASE2-10: la
     * pausa es {@code pausado_alerta}, separada de la de Denuncia y con prioridad
     * sobre ella (ver {@link #pausarPorAlerta}). */
    @EventListener
    @Transactional
    public void onSesionKillswitchMenor(SesionKillswitchMenorEvent evento) {
        pausarPorAlerta(evento.getReservaId());
    }

    /** {@code sesion.killswitch_adultos}: igual que la rama menor (ADR-M3-02). El
     * reembolso, cuando llegue, es total INCLUSO si el detectado es el propio
     * pagador (FR-PAG-012) — {@code detectadoId} se ignora a propósito. */
    @EventListener
    @Transactional
    public void onSesionKillswitchAdultos(SesionKillswitchAdultosEvent evento) {
        pausarPorAlerta(evento.getReservaId());
    }

    /** {@code alerta.resuelta} (M9 → M5, ADR-M3-02): cierra la pausa del kill-switch
     * con reembolso total al Estudiante, sea {@code reactivar} o {@code sancionar}.
     * FASE2-10 (AUD-005): solo actúa sobre {@code pausado_alerta}. */
    @EventListener
    @Transactional
    public void onAlertaResuelta(AlertaResueltaEvent evento) {
        transaccionRepo.findByReservaId(evento.getReservaId()).ifPresent(t -> {
            if (t.getEstado() != EstadoTransaccion.PAUSADO_ALERTA) {
                return;
            }
            t.setEstado(EstadoTransaccion.RETENIDO_ESCROW);
            transaccionRepo.save(t);
            reembolsarSiRetenida(evento.getReservaId());
        });
    }

    /** {@code denuncia.registrada} (Spec_M5 §2): pausa el escrow hasta la
     * resolución y cancela la ventana de liberación si ya estaba programada. La
     * reanudación (denuncia.resuelta) queda pendiente de M9-D + M5-C/M5-D. */
    @EventListener
    @Transactional
    public void onDenunciaRegistrada(DenunciaRegistradaEvent evento) {
        pausarSiRetenida(evento.getReservaId());
    }

    private void pausarSiRetenida(UUID reservaId) {
        transaccionRepo.findByReservaId(reservaId).ifPresent(t -> {
            if (t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW) {
                t.setEstado(EstadoTransaccion.PAUSADO_DENUNCIA);
                t.setLiberarAt(null);
                transaccionRepo.save(t);
                liberacionEscrow.cancelarLiberacion(t.getId());
            }
        });
    }

    /** FASE2-10 (AUD-005, riesgo abierto aceptado en ADR-M3-02): pausa por ALERTA
     * de seguridad, el track del menor (Artículo II) — manda sobre la Denuncia:
     * aparta el escrow en {@code pausado_alerta} si está {@code retenido_escrow}
     * O ya {@code pausado_denuncia}, y cancela la ventana de liberación. Sobre la
     * pausa de Alerta solo cierra {@link #onAlertaResuelta}; una Denuncia
     * posterior ni lo re-baja ni una resolución de Denuncia lo destraba
     * ({@link #onDenunciaResuelta}). */
    private void pausarPorAlerta(UUID reservaId) {
        transaccionRepo.findByReservaId(reservaId).ifPresent(t -> {
            if (t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW
                    || t.getEstado() == EstadoTransaccion.PAUSADO_DENUNCIA) {
                t.setEstado(EstadoTransaccion.PAUSADO_ALERTA);
                t.setLiberarAt(null);
                transaccionRepo.save(t);
                liberacionEscrow.cancelarLiberacion(t.getId());
            }
        });
    }

    /** {@code denuncia.resuelta} (Spec_M5 §2, T-M9-04): cierra la pausa del
     * escrow de ESA sesión puntual (FR-SEC-011 — cada denuncia libera su propio
     * escrow, sin esperar a denuncias cruzadas). Todas las ramas vuelven
     * {@code pausado_denuncia} → {@code retenido_escrow} para reusar las rutas
     * existentes (guard de estado en {@code ejecutarLiberacion}/{@code
     * reembolsarSiRetenida}): infundada → re-cuenta la liberación estándar de
     * 24hs (FR-SEC-011); fundada → se paga el trabajo ya realizado al Tutor
     * (FR-PAG-011); escalada → reembolso total al Estudiante (FR-PAG-009). Un
     * evento sin {@code reservaId} (denuncia de perfil) o sin resolución
     * (contrato mínimo del stub) no mueve dinero. FASE2-10: sobre una pausa por
     * ALERTA ({@code pausado_alerta}) es no-op con log — la Alerta de seguridad
     * manda y la destraba {@link #onAlertaResuelta}. */
    @EventListener
    @Transactional
    public void onDenunciaResuelta(DenunciaResueltaEvent evento) {
        UUID reservaId = evento.getReservaId();
        ResolucionDenuncia resolucion = evento.getResolucion();
        if (reservaId == null || resolucion == null) {
            return;
        }
        transaccionRepo.findByReservaId(reservaId).ifPresent(t -> {
            if (t.getEstado() == EstadoTransaccion.PAUSADO_ALERTA) {
                LOG.info("Denuncia {} resuelta como {} sin tocar el escrow: la reserva {} sigue "
                        + "pausada por Alerta de seguridad (FASE2-10)", evento.getDenunciaId(),
                        resolucion, reservaId);
                return;
            }
            if (t.getEstado() != EstadoTransaccion.PAUSADO_DENUNCIA) {
                return; // ya resuelta por otra vía → no-op
            }
            t.setEstado(EstadoTransaccion.RETENIDO_ESCROW);
            transaccionRepo.save(t);
            switch (resolucion) {
                case INFUNDADA -> {
                    t.setLiberarAt(Instant.now().plus(VENTANA_LIBERACION));
                    transaccionRepo.save(t);
                    liberacionEscrow.programarLiberacion(t.getId(), t.getLiberarAt());
                }
                case FUNDADA -> liberacionEscrow.ejecutarLiberacion(t.getId());
                case ESCALADA -> reembolsarSiRetenida(reservaId);
            }
        });
    }

    private void reembolsarSiRetenida(UUID reservaId) {
        transaccionRepo.findByReservaId(reservaId).ifPresent(t -> {
            if (t.getEstado() == EstadoTransaccion.RETENIDO_ESCROW) {
                // Única vía de reembolso (Plan §3.3, FR-PAG-009): el port real de
                // M5-D llama a MP con body vacío. En bypass (V22) no hay dinero
                // real → el reembolso es solo el cambio de estado local, sin
                // llamar al proveedor con un id falso.
                if (!t.isEnBypass()) {
                    reembolso.reembolsarTotal(t);
                }
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
                    if (PoliticaCancelacion.reembolsoTotal(
                            reserva, evento.getCanceladaPorUsuarioId(), Instant.now())) {
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
     * se preserva la unicidad de {@code findByReservaId} (Optional, usado en 6
     * puntos más de esta clase y en DenunciaService — dos filas por reserva_id
     * los rompería a todos con IncorrectResultSizeDataAccessException). */
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
        // Auditoría 2026-09-18: cuando la fila de arriba NO se persiste (ya
        // existía otra Transaccion para esta reserva), el guard de
        // findByMpPaymentId del inicio de procesarPagoAprobado nunca va a
        // encontrar esta operación en un reintento del webhook — MercadoPago
        // SÍ reintenta envíos sin 2xx (javadoc de la clase). Sin este catch,
        // reembolsar un pago que el intento anterior ya reembolsó tira
        // MercadoPagoNoDisponibleException sin capturar → 5xx → MP reintenta
        // de nuevo, en loop. No hay pérdida de dinero (MP no duplica un
        // refund ya aplicado), pero sí ruido de webhooks fallidos invisible
        // para Soporte Financiero. Idempotencia real (ancla persistida para
        // TODO reintento, sin la limitación de una fila por reserva) queda
        // pendiente de una migración dedicada — este fix corta el síntoma
        // más dañino (el loop) sin tocar el esquema.
        try {
            reembolso.reembolsarTotal(tardia);
        } catch (RuntimeException e) {
            LOG.warn("Reembolso de pago tardío falló para mpPaymentId={} (reservaId={}) — "
                    + "puede ser un reintento de un pago que un intento previo ya reembolsó; "
                    + "no se relanza para no generar un loop de reintentos del webhook de MP. "
                    + "Verificar manualmente en el dashboard de MercadoPago si el motivo no es "
                    + "un duplicado.", pago.mpPaymentId(), reserva.getId(), e);
        }
    }
}