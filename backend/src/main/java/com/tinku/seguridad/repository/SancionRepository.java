package com.tinku.seguridad.repository;

import com.tinku.seguridad.model.Sancion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SancionRepository extends JpaRepository<Sancion, UUID> {

    Optional<Sancion> findByDenunciaId(UUID denunciaId);

    Optional<Sancion> findByAlertaId(UUID alertaId);

    /**
     * ¿Tiene el usuario una sanción que lo mantiene fuera? (AUD-013): definitiva o baneo
     * (sin vencimiento), o temporal todavía no vencida. Una advertencia no cuenta.
     */
    @Query("""
            select count(s) > 0 from Sancion s
            where s.usuarioSancionadoId = :usuarioId
              and (s.tipo in (com.tinku.seguridad.model.TipoSancion.SUSPENSION_DEFINITIVA,
                              com.tinku.seguridad.model.TipoSancion.BANEO_AUTORIDADES)
                   or (s.tipo = com.tinku.seguridad.model.TipoSancion.SUSPENSION_TEMPORAL
                       and s.vigenteHasta > :ahora))
            """)
    boolean existeSancionVigente(@Param("usuarioId") UUID usuarioId, @Param("ahora") Instant ahora);
}