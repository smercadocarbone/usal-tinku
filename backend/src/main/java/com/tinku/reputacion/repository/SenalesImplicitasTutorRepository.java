package com.tinku.reputacion.repository;

import com.tinku.reputacion.model.SenalesImplicitasTutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SenalesImplicitasTutorRepository extends JpaRepository<SenalesImplicitasTutor, UUID> {

    List<SenalesImplicitasTutor> findByTutorIdIn(Collection<UUID> tutorIds);
}
