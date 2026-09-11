package com.tinku.aula.repository;

import com.tinku.aula.model.ConfirmacionKillswitch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfirmacionKillswitchRepository extends JpaRepository<ConfirmacionKillswitch, UUID> {

    Optional<ConfirmacionKillswitch> findBySesionId(UUID sesionId);
}
