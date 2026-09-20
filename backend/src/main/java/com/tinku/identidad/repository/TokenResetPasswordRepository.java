package com.tinku.identidad.repository;

import com.tinku.identidad.model.TokenResetPassword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TokenResetPasswordRepository extends JpaRepository<TokenResetPassword, UUID> {
    Optional<TokenResetPassword> findByTokenHash(String tokenHash);

    /** Antes de emitir uno nuevo, invalidar los pendientes del mismo usuario —
     * que un link viejo (todavía sin usar/vencer) siga siendo válido después
     * de pedir uno nuevo es una superficie de ataque innecesaria. */
    void deleteByUsuarioIdAndUsadoEnIsNull(UUID usuarioId);
}
