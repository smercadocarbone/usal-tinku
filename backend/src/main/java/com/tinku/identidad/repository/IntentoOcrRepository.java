package com.tinku.identidad.repository;

import com.tinku.identidad.model.IntentoOcr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntentoOcrRepository extends JpaRepository<IntentoOcr, String> {
    Optional<IntentoOcr> findByDni(String dni);
}
