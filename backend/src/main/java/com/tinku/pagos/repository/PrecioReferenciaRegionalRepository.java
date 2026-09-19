package com.tinku.pagos.repository;

import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.model.PrecioReferenciaRegionalId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@code pagos.precios_referencia_regional} (T-M5-09, US-6). La versión
 * vigente de una provincia es la de mayor {@code version} — las revisiones
 * trimestrales de M8 acumulan filas (FR-ADM-007), nunca sobreescriben.
 */
public interface PrecioReferenciaRegionalRepository
        extends JpaRepository<PrecioReferenciaRegional, PrecioReferenciaRegionalId> {

    Optional<PrecioReferenciaRegional> findFirstByProvinciaOrderByVersionDesc(String provincia);

    /** Auditoría 2026-09-18 (gap del frontend, panel M8): la fila VIGENTE
     *  (mayor version) de CADA provincia — antes solo existía POST a ciegas,
     *  sin forma de ver la tabla antes de publicar una versión nueva. */
    @Query("""
            select p from PrecioReferenciaRegional p
             where p.version = (
                select max(p2.version) from PrecioReferenciaRegional p2
                 where p2.provincia = p.provincia
             )
             order by p.provincia
            """)
    List<PrecioReferenciaRegional> vigentesPorProvincia();
}