package com.tinku.pagos.repository;

import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a {@code pagos.transacciones}. El webhook de M5-B consulta por
 * {@code mpPaymentId} para la idempotencia (MP reintenta los no-2xx, nunca
 * duplicar el escrow) y por {@code reservaId} desde los listeners de eventos.
 * {@link #findByEstadoAndIntentosLiberacion} expone la cola de liberaciones
 * fallidas (FR-PAG-007): transacciones {@code retenido_escrow} con
 * {@code intentos_liberacion} agotados que el Admin de Soporte Financiero (M8)
 * interviene manualmente.
 */
public interface TransaccionRepository extends JpaRepository<Transaccion, UUID> {

    Optional<Transaccion> findByMpPaymentId(String mpPaymentId);

    Optional<Transaccion> findByReservaId(UUID reservaId);

    /** R5 "Mis cobros": [Transaccion, Reserva] de un Tutor, la clase más reciente primero. */
    @org.springframework.data.jpa.repository.Query("select t, r from Transaccion t, com.tinku.reservas.model.Reserva r "
            + "where r.id = t.reservaId and r.tutor.id = :tutorId order by r.horario desc")
    List<Object[]> cobrosDelTutor(UUID tutorId, org.springframework.data.domain.Pageable pagina);

    /** R4: cola de reembolsos del adicional por estado. */
    List<Transaccion> findByAdicionalReembolsoEstadoOrderByCreatedAtAsc(
            com.tinku.pagos.model.EstadoReembolsoAdicional estado);

    /**
     * ¿Alguna {@code Transaccion} para la Reserva? La usa el reembolso de pagos
     * tardíos (T-M5-07): si la Reserva llegó a tener escrow, un segundo pago
     * sobre la misma {@code external_reference} se reembolsa SIN crear una fila
     * nueva (se preserva la unicidad de {@link #findByReservaId}); si nunca tuvo
     * escrow (timeout/cancelación de un {@code pendiente_pago}), se registra una
     * {@code Transaccion} {@code reembolsado} como ancla de idempotencia y
     * auditoría.
     */
    boolean existsByReservaId(UUID reservaId);

    List<Transaccion> findByEstadoAndIntentosLiberacion(
            EstadoTransaccion estado, int intentosLiberacion);

    /**
     * Cola de intervención manual de M8 (US-5, FR-PAG-007): el Admin de Soporte
     * Financiero ve las transacciones {@code retenido_escrow} que AGOTARON los
     * reintentos automáticos ({@code intentos_liberacion >= 3}). Distinto de
     * {@link #findByEstadoAndIntentosLiberacion} (igualdad): acá el ítem de la
     * cola es el escrow que ya se salió del flujo automático de M5.
     */
    List<Transaccion> findByEstadoAndIntentosLiberacionGreaterThanEqual(
            EstadoTransaccion estado, int intentosLiberacion);
}