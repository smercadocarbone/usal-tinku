package com.tinku.admin.repository;

import com.tinku.admin.model.Admin;
import com.tinku.admin.model.RolAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a {@code admin.admins} (V16). El gate de autorización
 * ({@code com.tinku.shared.AdminModeracionGate}) resuelve la fila por el DNI del
 * principal del JWT ({@code UsuarioDetailsService} autentica contra
 * {@code identidad.usuarios.dni}; este repo cruza por {@code Admin#getUsuario()}).
 */
public interface AdminRepository extends JpaRepository<Admin, UUID> {

    /** Admin activo con ese rol para un DNI — fail-closed: sin fila → 403. */
    Optional<Admin> findByUsuario_DniAndRolAndActivoTrue(String dni, RolAdmin rol);

    /** Admin activo (cualquier rol) — para atribución en auditoría y tickets. */
    Optional<Admin> findByUsuario_DniAndActivoTrue(String dni);
}