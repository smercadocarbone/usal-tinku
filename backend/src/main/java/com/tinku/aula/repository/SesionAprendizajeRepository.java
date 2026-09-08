package com.tinku.aula.repository;

import com.tinku.aula.model.SesionAprendizaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SesionAprendizajeRepository extends JpaRepository<SesionAprendizaje, UUID> {

    Optional<SesionAprendizaje> findByLivekitRoomId(String livekitRoomId);

    /** 1:1 con la Reserva (reserva_id UNIQUE, V8). */
    Optional<SesionAprendizaje> findByReservaId(UUID reservaId);
}