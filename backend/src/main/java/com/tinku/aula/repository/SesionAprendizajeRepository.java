package com.tinku.aula.repository;

import com.tinku.aula.model.SesionAprendizaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SesionAprendizajeRepository extends JpaRepository<SesionAprendizaje, UUID> {

    /** Con lock de fila: dos disparos simultáneos del kill-switch se serializan. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SesionAprendizaje s where s.id = :id")
    java.util.Optional<com.tinku.aula.model.SesionAprendizaje> findByIdParaActualizar(
            @org.springframework.data.repository.query.Param("id") java.util.UUID id);


    Optional<SesionAprendizaje> findByLivekitRoomId(String livekitRoomId);

    /**
     * Con lock de fila, para el webhook de LiveKit: un participant_left que llega durante el corte
     * del kill-switch espera a que el corte termine y ve la sesión ya cerrada, en vez de pisarla
     * con lo que había leído antes (M3C-06).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SesionAprendizaje s where s.livekitRoomId = :sala")
    Optional<SesionAprendizaje> findByLivekitRoomIdParaActualizar(
            @org.springframework.data.repository.query.Param("sala") String livekitRoomId);

    /** 1:1 con la Reserva (reserva_id UNIQUE, V8). */
    Optional<SesionAprendizaje> findByReservaId(UUID reservaId);
}