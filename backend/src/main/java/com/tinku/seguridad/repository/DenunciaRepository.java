package com.tinku.seguridad.repository;

import com.tinku.seguridad.model.Denuncia;
import com.tinku.seguridad.model.EstadoDenuncia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DenunciaRepository extends JpaRepository<Denuncia, UUID> {

    /** Cola de moderación (M8): pendientes de resolver, las de prioridad alta primero. */
    List<Denuncia> findAllByEstadoInOrderByPrioridadAltaDescCreatedAtAsc(
            List<EstadoDenuncia> estados);

    /** Denuncia ACTIVA sobre una sesión puntual (T-M6-03): M6 pausa la generación
     *  del resumen si existe {@code registrada}/{@code en_revision} (FR-SUM-008). */
    boolean existsBySesionIdAndEstadoIn(UUID sesionId, Collection<EstadoDenuncia> estados);
}