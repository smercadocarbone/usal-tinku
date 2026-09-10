package com.tinku.admin.repository;

import com.tinku.admin.model.MapeoOrigenRol;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code admin.mapeo_origen_rol} (T-M8-05) — la tabla de config que
 * decide el rol destino de cada ticket según su {@code origen_modulo}.
 */
public interface MapeoOrigenRolRepository extends JpaRepository<MapeoOrigenRol, String> {
}