package com.tinku.pagos.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.model.TarifaTutor;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaRequest;
import com.tinku.pagos.repository.PrecioReferenciaRegionalRepository;
import com.tinku.pagos.repository.TarifaTutorRepository;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservaNoEncontradaException;
import com.tinku.reservas.service.ReservaService;
import com.tinku.reservas.service.SoloTutorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Motor de pagos — Chunk M5-A (T-M5-02): generación de la preferencia de pago
 * de MercadoPago para una Reserva en {@code pendiente_pago} (Plan M5 §3.1, US-1).
 * Chunk M5-E (T-M5-09): sugerencia de precio de referencia regional (US-6).
 *
 * El cobro va a escrow: la preferencia se crea con {@code marketplace_fee} =
 * comisión de la plataforma (BR-PAG-01), y es el webhook de M5-B quien crea la
 * fila en {@code pagos.transacciones} y confirma la Reserva al recibir el pago
 * aprobado. Este servicio solo registra la preferencia para la conciliación (R2).
 */
@Service
public class PagoService {

    private static final String DESCRIPCION_ITEM = "Sesión de tutoría Tinku";
    private static final String DESCRIPCION_PAQUETE = "Paquete de 4 clases de tutoría Tinku";

    private final ReservaRepository reservaRepo;
    private final MercadoPagoClient mercadopago;
    private final ComisionPlataforma comision;
    private final PrecioReferenciaRegionalRepository precioReferenciaRepo;
    private final TarifaTutorRepository tarifaTutorRepo;
    private final TransaccionRepository transaccionRepo;
    private final ReservaService reservaService;
    private final PasarelaService pasarela;
    private final PisoTarifa pisoTarifa;
    private final ConciliacionPagosService conciliacion;

    public PagoService(ReservaRepository reservaRepo,
                       MercadoPagoClient mercadopago,
                       ComisionPlataforma comision,
                       PrecioReferenciaRegionalRepository precioReferenciaRepo,
                       TarifaTutorRepository tarifaTutorRepo,
                       TransaccionRepository transaccionRepo,
                       ReservaService reservaService,
                       PasarelaService pasarela,
                       PisoTarifa pisoTarifa,
                       ConciliacionPagosService conciliacion) {
        this.reservaRepo = reservaRepo;
        this.mercadopago = mercadopago;
        this.comision = comision;
        this.precioReferenciaRepo = precioReferenciaRepo;
        this.tarifaTutorRepo = tarifaTutorRepo;
        this.transaccionRepo = transaccionRepo;
        this.reservaService = reservaService;
        this.pasarela = pasarela;
        this.pisoTarifa = pisoTarifa;
        this.conciliacion = conciliacion;
    }

    /** Solo el pagador de la reserva (Artículo II) puede pedir que se confirme su pago. */
    @Transactional(readOnly = true)
    public void exigirPagador(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getPagador() == null || !reserva.getPagador().getId().equals(usuario.getId())) {
            throw new SoloPagadorPreferenciaException();
        }
    }

    @Transactional
    public PreferenciaPago generarPreferencia(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            throw new PreferenciaNoDisponibleException();
        }
        // Artículo II: el pagador es Estudiante adulto o Adulto Responsable — nunca
        // el menor ni el Tutor. Un menor que intente esto no es el pagador → 403.
        if (reserva.getPagador() == null
                || !reserva.getPagador().getId().equals(usuario.getId())) {
            throw new SoloPagadorPreferenciaException();
        }
        // ADR-M5-03: un paquete se paga entero desde su clase ancla.
        if (reserva.getPaquete() != null) {
            return generarPreferenciaPaquete(reserva);
        }
        // FR-PAG-013: el monto es el precio congelado al crear la Reserva (M4).
        // BR-PAG-01: comisión compartida con EscrowService vía ComisionPlataforma.
        BigDecimal comision = this.comision.calcular(reserva.getPrecio());
        if (!pasarela.estaHabilitada()) {
            return confirmarEnBypass(reserva, comision);
        }
        // T09: se cobra sesión + adicional; el adicional va íntegro a la plataforma
        // (marketplace_fee = comisión sobre la sesión + adicional). La comisión NO se
        // calcula sobre el adicional.
        // R2: la preferencia vence junto con la Reserva sin pagar (Tabla de Tiempos) y queda
        // registrada para conciliar el pago aunque no vuelva el navegador ni llegue el webhook.
        // ADR-M5-02: con el token del Tutor (vendedor) para que MP reparta con marketplace_fee.
        String tokenVendedor = conciliacion.tokenVendedor(reserva.getTutor().getId());
        PreferenciaPago preferencia = mercadopago.crearPreferencia(new PreferenciaRequest(
                reserva.getId(), reserva.montoTotal(), comision.add(adicional(reserva)), DESCRIPCION_ITEM,
                reserva.getCreatedAt().plus(ReservaService.TIMEOUT_PENDIENTE_PAGO)), tokenVendedor);
        conciliacion.registrarPreferencia(reserva.getId(), preferencia.preferenceId());
        return preferencia;
    }

    /**
     * FR-PAG-021 (ADR-M5-03): una preferencia por el total del paquete, con
     * {@code external_reference} = la clase ancla (la conciliación, la vuelta del navegador y el
     * webhook siguen trabajando por id de Reserva). {@code marketplace_fee} = la suma de la
     * comisión de cada clase, el mismo número que después registra cada {@code Transaccion}.
     */
    private PreferenciaPago generarPreferenciaPaquete(Reserva ancla) {
        com.tinku.reservas.model.Paquete paquete = ancla.getPaquete();
        if (!ancla.getId().equals(paquete.getReservaAnclaId())) {
            throw new PreferenciaNoDisponibleException();
        }
        List<Reserva> clases = reservaRepo.findByPaquete_IdOrderByHorario(paquete.getId());
        if (clases.stream().anyMatch(r -> r.getEstado() != EstadoReserva.PENDIENTE_PAGO)) {
            throw new PreferenciaNoDisponibleException();
        }
        BigDecimal comisionTotal = clases.stream().map(r -> comision.calcular(r.getPrecio()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!pasarela.estaHabilitada()) {
            for (Reserva clase : clases) {
                if (!transaccionRepo.existsByReservaId(clase.getId())) {
                    Transaccion t = new Transaccion();
                    t.setReservaId(clase.getId());
                    t.setMpPaymentId("bypass-" + clase.getId());
                    t.setMontoBruto(clase.getPrecio());
                    t.setComisionPlataforma(comision.calcular(clase.getPrecio()));
                    t.setEnBypass(true);
                    transaccionRepo.save(t);
                }
            }
            paquete.setEstado(com.tinku.reservas.model.Paquete.CONFIRMADO);
            clases.forEach(c -> reservaService.confirmarPagoSimulado(c.getId()));
            return new PreferenciaPago("bypass-" + ancla.getId(), null, true);
        }
        String tokenVendedor = conciliacion.tokenVendedor(ancla.getTutor().getId());
        PreferenciaPago preferencia = mercadopago.crearPreferencia(new PreferenciaRequest(
                ancla.getId(), paquete.getPrecioTotal(), comisionTotal, DESCRIPCION_PAQUETE,
                ancla.getCreatedAt().plus(ReservaService.TIMEOUT_PENDIENTE_PAGO)), tokenVendedor);
        conciliacion.registrarPreferencia(ancla.getId(), preferencia.preferenceId());
        return preferencia;
    }

    /**
     * Modo Bypass (V22): la pasarela está deshabilitada → NO se llama a
     * MercadoPago. Se crea la {@code Transaccion} en escrow marcada
     * {@code en_bypass=true} (sin dinero real) y se confirma la Reserva con el
     * MISMO flujo que el webhook ({@code confirmarPagoSimulado} → M3 crea la
     * Sesión). El {@code initPoint} queda {@code null} y {@code bypass=true}: el
     * frontend lo interpreta como "pago simulado". Idempotente: si la Reserva ya
     * tiene escrow, no se duplica la transacción.
     */
    private PreferenciaPago confirmarEnBypass(Reserva reserva, BigDecimal comision) {
        if (!transaccionRepo.existsByReservaId(reserva.getId())) {
            Transaccion transaccion = new Transaccion();
            transaccion.setReservaId(reserva.getId());
            transaccion.setMpPaymentId("bypass-" + reserva.getId());
            transaccion.setMontoBruto(reserva.montoTotal());
            transaccion.setComisionPlataforma(comision);
            transaccion.setMontoAdicionalResumen(adicional(reserva));
            transaccion.setEnBypass(true);
            transaccionRepo.save(transaccion);
        }
        reservaService.confirmarPagoSimulado(reserva.getId());
        return new PreferenciaPago("bypass-" + reserva.getId(), null, true);
    }

    // ------------------------------------------------------ US-6 (T-M5-09)

    /**
     * Sugerencia de precio de referencia regional (US-6, FR-PAG-005/006):
     * devuelve la versión vigente (mayor {@code version}, Plan M5 §3.4) de la
     * provincia. El valor es NO vinculante y el consumidor lo COPIA al perfil
     * del Tutor sin FK a la fila — una revisión trimestral de M8 (versión nueva)
     * no afecta retroactivamente a quien ya fijó su precio (FR-PAG-006).
     */
    public PrecioReferenciaRegional sugerirPrecioReferencia(String provincia) {
        if (provincia == null || provincia.isBlank()) {
            throw new ProvinciaSinPrecioReferenciaException(provincia);
        }
        return precioReferenciaRepo.findFirstByProvinciaOrderByVersionDesc(provincia.trim())
                .orElseThrow(() -> new ProvinciaSinPrecioReferenciaException(provincia));
    }

    // ------------------------------------------------------ US-6 (M5-H, tarifa del Tutor)

    /**
     * El Tutor fija el precio por hora de su perfil (Spec M5 US-6, FR-PAG-006,
     * Chunk M5-H). Upsert sobre {@code pagos.tarifas_tutor}: una fila por Tutor,
     * se actualiza in-place cuando él cambia su precio. FR-PAG-013 garantiza que
     * las Reservas ya creadas conservan su precio congelado — este cambio solo
     * aplica hacia adelante. T06: no puede quedar por debajo del piso por hora.
     */
    @Transactional
    public TarifaTutor actualizarTarifaTutor(Usuario tutor, BigDecimal precioHora) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException("Solo las cuentas de Tutor pueden fijar su tarifa por hora.");
        }
        pisoTarifa.exigir(precioHora);
        TarifaTutor tarifa = tarifaTutorRepo.findByTutorId(tutor.getId())
                .orElseGet(() -> {
                    TarifaTutor nueva = new TarifaTutor();
                    nueva.setTutorId(tutor.getId());
                    return nueva;
                });
        tarifa.setPrecioHora(precioHora);
        tarifa.setUpdatedAt(Instant.now());
        return tarifaTutorRepo.save(tarifa);
    }

    /** v2.5 (ADR-M5-03): tope del descuento del paquete. */
    public static final int MAX_DESCUENTO_PAQUETE = 30;

    /**
     * El Tutor ofrece (o deja de ofrecer) el paquete mensual con un descuento de 0 a 30 %
     * (ADR-M5-03). Necesita una tarifa configurada, y el precio por hora con el descuento no
     * puede quedar por debajo del piso (T06). Solo hacia adelante: los paquetes ya creados
     * congelaron su precio (FR-PAG-013).
     */
    @Transactional
    public TarifaTutor configurarPaquete(Usuario tutor, boolean habilitado, int descuentoPorcentaje) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException("Solo las cuentas de Tutor ofrecen paquetes.");
        }
        if (descuentoPorcentaje < 0 || descuentoPorcentaje > MAX_DESCUENTO_PAQUETE) {
            throw new PaqueteConfigInvalidaException("El descuento tiene que ser de 0 a " + MAX_DESCUENTO_PAQUETE + " %.");
        }
        TarifaTutor tarifa = tarifaTutorRepo.findByTutorId(tutor.getId())
                .orElseThrow(() -> new PaqueteConfigInvalidaException("Primero configurá tu precio por hora."));
        if (habilitado) {
            BigDecimal conDescuento = tarifa.getPrecioHora()
                    .multiply(BigDecimal.valueOf(100 - descuentoPorcentaje))
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            if (conDescuento.compareTo(pisoTarifa.pisoHora()) < 0) {
                throw new PaqueteConfigInvalidaException("Con ese descuento la hora queda por debajo del mínimo de $"
                        + pisoTarifa.pisoHora().toPlainString() + ". Probá con un descuento menor.");
            }
        }
        tarifa.setPaqueteHabilitado(habilitado);
        tarifa.setPaqueteDescuentoPorcentaje(descuentoPorcentaje);
        return tarifaTutorRepo.save(tarifa);
    }

    /** T09: el adicional de resumen de la Reserva, o cero. */
    static BigDecimal adicional(Reserva reserva) {
        return reserva.getPrecioAdicionalResumen() == null ? BigDecimal.ZERO : reserva.getPrecioAdicionalResumen();
    }
}
