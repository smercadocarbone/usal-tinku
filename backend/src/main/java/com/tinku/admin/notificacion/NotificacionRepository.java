package com.tinku.admin.notificacion;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    Page<Notificacion> findByDestinatarioIdOrderByCreadaAtDesc(UUID destinatarioId, Pageable pageable);

    Optional<Notificacion> findByIdAndDestinatarioId(UUID id, UUID destinatarioId);

    long countByDestinatarioIdAndLeidaAtIsNull(UUID destinatarioId);

    List<Notificacion> findByDestinatarioId(UUID destinatarioId);

    /** Emails vencidos para enviar. {@code SKIP LOCKED}: dos barridos en paralelo nunca
     *  toman la misma fila (no hay doble envío). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select n from Notificacion n where n.enviadaEmailAt is null and n.emailDescartadoAt is null"
            + " and n.proximoIntentoEmailAt is not null and n.proximoIntentoEmailAt <= :ahora"
            + " order by n.proximoIntentoEmailAt")
    List<Notificacion> pendientesDeEmail(Instant ahora, Pageable pageable);
}
