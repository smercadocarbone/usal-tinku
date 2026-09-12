package com.tinku.pagos.repository;

import com.tinku.pagos.model.EstadoPasarela;
import org.springframework.data.jpa.repository.JpaRepository;

/** Fila única de {@code pagos.pasarela_estado} (V22) — gestión del modo Bypass. */
public interface PasarelaEstadoRepository extends JpaRepository<EstadoPasarela, Short> {
}