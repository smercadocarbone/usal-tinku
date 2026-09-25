package com.tinku.pagos.repository;

import com.tinku.pagos.model.PreferenciaMp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PreferenciaMpRepository extends JpaRepository<PreferenciaMp, UUID> {

    /** Preferencias que el barrido de conciliación todavía mira (R2): sin conciliar, dentro de la ventana. */
    @Query("select p.reservaId from PreferenciaMp p where p.conciliadoAt is null "
            + "and p.createdAt > :desde and p.createdAt < :hasta order by p.createdAt")
    List<UUID> pendientesDeConciliar(Instant desde, Instant hasta);
}
