package com.tinku.pagos.repository;

import com.tinku.pagos.model.CuentaMpTutor;
import com.tinku.pagos.model.EstadoCuentaMp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CuentaMpTutorRepository extends JpaRepository<CuentaMpTutor, UUID> {

    Optional<CuentaMpTutor> findFirstByMpUserIdAndEstado(String mpUserId, EstadoCuentaMp estado);

    @Query("select c.tutorId from CuentaMpTutor c where c.estado = com.tinku.pagos.model.EstadoCuentaMp.CONECTADA "
            + "and c.tutorId in :tutores")
    List<UUID> conectadasEntre(Collection<UUID> tutores);

    List<CuentaMpTutor> findByEstadoAndExpiraAtBefore(EstadoCuentaMp estado, Instant limite);
}
