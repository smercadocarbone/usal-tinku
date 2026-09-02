package com.tinku.identidad.repository;

import com.tinku.identidad.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    boolean existsByDni(String dni);
    Optional<Usuario> findByDni(String dni);
}
