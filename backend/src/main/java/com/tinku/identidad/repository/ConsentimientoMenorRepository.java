package com.tinku.identidad.repository;

import com.tinku.identidad.model.ConsentimientoMenor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsentimientoMenorRepository extends JpaRepository<ConsentimientoMenor, UUID> {
}
