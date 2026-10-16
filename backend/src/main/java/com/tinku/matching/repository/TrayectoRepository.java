package com.tinku.matching.repository;

import com.tinku.matching.model.Trayecto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Lectura de trayectos del catálogo (se usa para sembrar fixtures en los
 * tests de integración; el árbol en producción se lee via {@link TemaRepository}). */
public interface TrayectoRepository extends JpaRepository<Trayecto, UUID> {

    /** ADR-M1-06: materias del catálogo, sin repetir (las especialidades del Tutor se eligen de acá). */
    @org.springframework.data.jpa.repository.Query("select distinct t.materia from Trayecto t order by t.materia")
    java.util.List<String> materiasDistintas();
}