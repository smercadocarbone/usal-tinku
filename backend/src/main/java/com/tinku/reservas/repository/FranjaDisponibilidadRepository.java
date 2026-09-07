package com.tinku.reservas.repository;

import com.tinku.reservas.model.FranjaDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FranjaDisponibilidadRepository extends JpaRepository<FranjaDisponibilidad, UUID> {

    List<FranjaDisponibilidad> findByTutorIdAndActivaTrueOrderByHoraInicio(UUID tutorId);
}