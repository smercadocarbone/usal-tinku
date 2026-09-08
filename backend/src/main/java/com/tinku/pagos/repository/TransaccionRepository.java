package com.tinku.pagos.repository;

import com.tinku.pagos.model.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a {@code pagos.transacciones}. El webhook de M5-B consulta por
 * {@code mpPaymentId} para la idempotencia (MP reintenta los no-2xx, nunca
 * duplicar el escrow) y por {@code reservaId} desde los listeners de eventos.
 */
public interface TransaccionRepository extends JpaRepository<Transaccion, UUID> {

    Optional<Transaccion> findByMpPaymentId(String mpPaymentId);

    Optional<Transaccion> findByReservaId(UUID reservaId);
}