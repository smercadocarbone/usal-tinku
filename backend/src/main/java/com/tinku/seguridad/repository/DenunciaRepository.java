package com.tinku.seguridad.repository;

import com.tinku.seguridad.model.Denuncia;
import com.tinku.seguridad.model.EstadoDenuncia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DenunciaRepository extends JpaRepository<Denuncia, UUID> {

    /** Cola de moderación (M8): pendientes de resolver, las de prioridad alta primero. */
    List<Denuncia> findAllByEstadoInOrderByPrioridadAltaDescCreatedAtAsc(
            List<EstadoDenuncia> estados);

    /**
     * Cola de revisión del Admin (US-3, T-M8-04): denuncias {@code en_revision}
     * con descargo ya recibido, ordenadas por urgencia real — prioridad alta
     * primero, y dentro de cada grupo por SLA más cercano a vencer
     * ({@code sla_resolucion_vence_at} ASC). No reemplaza al método de arriba
     * (que ordena por {@code created_at}): este expresa el plazo de la cola.
     */
    List<Denuncia> findByEstadoOrderByPrioridadAltaDescSlaResolucionVenceAtAsc(
            EstadoDenuncia estado);
}