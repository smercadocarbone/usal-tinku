package com.tinku.identidad.repository;

import com.tinku.identidad.model.IntentoCredencial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntentoCredencialRepository extends JpaRepository<IntentoCredencial, UUID> {
}
