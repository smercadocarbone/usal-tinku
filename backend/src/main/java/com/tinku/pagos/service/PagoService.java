package com.tinku.pagos.service;

import com.tinku.identidad.model.Usuario;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaRequest;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservaNoEncontradaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Motor de pagos — Chunk M5-A (T-M5-02): generación de la preferencia de pago
 * de MercadoPago para una Reserva en {@code pendiente_pago} (Plan M5 §3.1, US-1).
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
    private final int comisionPercent;

    public PagoService(ReservaRepository reservaRepo,
                       MercadoPagoClient mercadopago,
                       @Value("${tinku.mercadopago.marketplace-fee-percent:15}") int comisionPercent) {
        this.reservaRepo = reservaRepo;
        this.mercadopago = mercadopago;
        this.comisionPercent = comisionPercent;
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
        BigDecimal comision = comisionPlataforma(reserva.getPrecio());
        return mercadopago.crearPreferencia(new PreferenciaRequest(
                reserva.getId(), reserva.getPrecio(), comision, DESCRIPCION_ITEM));
    }

    /** BR-PAG-01: comisión de la plataforma = 15% del monto bruto (redondeo a
     * centavos, siempre sobre el precio congelado). */
    private BigDecimal comisionPlataforma(BigDecimal montoBruto) {
        return montoBruto.multiply(BigDecimal.valueOf(comisionPercent).movePointLeft(2))
                .setScale(2, RoundingMode.HALF_UP);
    }
}