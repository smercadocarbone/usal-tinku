package com.tinku.pagos.repository;

import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.model.PrecioReferenciaRegionalId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a {@code pagos.precios_referencia_regional} (T-M5-09, US-6). La versión
 * vigente de una provincia es la de mayor {@code version} — las revisiones
 * trimestrales de M8 acumulan filas (FR-ADM-007), nunca sobreescriben.
 */
public interface PrecioReferenciaRegionalRepository
        extends JpaRepository<PrecioReferenciaRegional, PrecioReferenciaRegionalId> {

    Optional<PrecioReferenciaRegional> findFirstByProvinciaOrderByVersionDesc(String provincia);
}