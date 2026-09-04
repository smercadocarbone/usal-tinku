package com.tinku.identidad.repository;

import com.tinku.identidad.model.ConsentimientoMenor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.UUID;

public interface ConsentimientoMenorRepository extends JpaRepository<ConsentimientoMenor, UUID> {

    /** Baja de menor (T-M1-12): se eliminan sus consentimientos. */
    @Modifying
    void deleteByMenorId(UUID menorId);
}
