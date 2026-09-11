package com.tinku.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Lectura de trayectos del catálogo (se usa para sembrar fixtures en los
 * tests de integración; el árbol en producción se lee via {@link TemaRepository}). */
public interface TrayectoRepository extends JpaRepository<Trayecto, UUID> {
}