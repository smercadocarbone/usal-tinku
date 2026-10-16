package com.tinku.pagos.service;

import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.repository.TransaccionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Lee una {@code Transaccion} con lock de fila ({@code SELECT ... FOR UPDATE}) y su estado
 * recién leído de la base. Todo lo que cambia el estado del escrow (liberar, reembolsar,
 * pausar) pasa por acá: sin el lock, dos operaciones a la vez (el job de liberación y una
 * denuncia, por ejemplo) leían {@code retenido_escrow} las dos y la última en commitear pisaba
 * a la otra. Se hace {@code flush} antes del {@code refresh} para no perder un cambio todavía
 * no escrito de la misma transacción (los listeners se llaman unos a otros).
 */
@Component
public class BloqueoTransaccion {

    private final TransaccionRepository transaccionRepo;

    @PersistenceContext
    private EntityManager em;

    public BloqueoTransaccion(TransaccionRepository transaccionRepo) {
        this.transaccionRepo = transaccionRepo;
    }

    public Optional<Transaccion> porId(UUID transaccionId) {
        return transaccionRepo.findById(transaccionId).map(this::bloquear);
    }

    public Optional<Transaccion> porReserva(UUID reservaId) {
        return transaccionRepo.findByReservaId(reservaId).map(this::bloquear);
    }

    private Transaccion bloquear(Transaccion t) {
        em.flush();
        em.refresh(t, LockModeType.PESSIMISTIC_WRITE);
        return t;
    }
}
