package com.tinku.pagos.repository;

import com.tinku.pagos.model.OAuthEstadoMp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface OAuthEstadoMpRepository extends JpaRepository<OAuthEstadoMp, String> {

    @Modifying
    @Query("delete from OAuthEstadoMp e where e.expiraAt < :ahora")
    int borrarVencidos(Instant ahora);
}
