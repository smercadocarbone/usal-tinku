package com.tinku.admin.repository;

import com.tinku.admin.model.LogAuditoriaAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Acceso de SOLO ESCRITURA a {@code admin.log_auditoria_admin} (insert-only).
 * La BD no permite UPDATE/DELETE a esta tabla (V16) — el repo ni siquiera
 * expone un método {@code save} sobre filas existentes: se construye siempre un
 * {@code LogAuditoriaAdmin} nuevo.
 */
public interface LogAuditoriaAdminRepository extends JpaRepository<LogAuditoriaAdmin, UUID> {
}