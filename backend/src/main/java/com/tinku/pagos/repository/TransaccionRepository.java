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

    List<Transaccion> findByEstadoAndIntentosLiberacion(
            EstadoTransaccion estado, int intentosLiberacion);
}