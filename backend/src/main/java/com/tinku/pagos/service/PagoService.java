package com.tinku.pagos.service;

import com.tinku.identidad.model.Usuario;
import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaRequest;
import com.tinku.pagos.repository.PrecioReferenciaRegionalRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservaNoEncontradaException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Motor de pagos — Chunk M5-A (T-M5-02): generación de la preferencia de pago
 * de MercadoPago para una Reserva en {@code pendiente_pago} (Plan M5 §3.1, US-1).
 * Chunk M5-E (T-M5-09): sugerencia de precio de referencia regional (US-6).
 *
 * El cobro va a escrow: la preferencia se crea con {@code marketplace_fee} =
 * comisión de la plataforma (BR-PAG-01), y es el webhook de M5-B quien crea la
 * fila en {@code pagos.transacciones} y confirma la Reserva al recibir el pago
 * aprobado — este servicio no persiste nada.
 */
@Service
public class PagoService {

    private static final String DESCRIPCION_ITEM = "Sesión de tutoría Tinku";

    private final ReservaRepository reservaRepo;
    private final MercadoPagoClient mercadopago;
    private final ComisionPlataforma comision;
    private final PrecioReferenciaRegionalRepository precioReferenciaRepo;

    public PagoService(ReservaRepository reservaRepo,
                       MercadoPagoClient mercadopago,
                       ComisionPlataforma comision,
                       PrecioReferenciaRegionalRepository precioReferenciaRepo) {
        this.reservaRepo = reservaRepo;
        this.mercadopago = mercadopago;
        this.comision = comision;
        this.precioReferenciaRepo = precioReferenciaRepo;
    }

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
        // FR-PAG-013: el monto es el precio congelado al crear la Reserva (M4).
        // BR-PAG-01: comisión compartida con EscrowService vía ComisionPlataforma.
        BigDecimal comision = this.comision.calcular(reserva.getPrecio());
        return mercadopago.crearPreferencia(new PreferenciaRequest(
                reserva.getId(), reserva.getPrecio(), comision, DESCRIPCION_ITEM));
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
}