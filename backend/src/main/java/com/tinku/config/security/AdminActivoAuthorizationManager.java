package com.tinku.config.security;

import com.tinku.admin.repository.AdminRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Defensa en profundidad para el filter chain de {@code /api/admin/**}: exige
 * que el principal autenticado (DNI, {@link com.tinku.identidad.service.UsuarioDetailsService})
 * tenga una fila activa en {@code admin.admins}, cualquiera sea su rol.
 *
 * No reemplaza el chequeo granular por rol de {@link com.tinku.admin.AdminModeracionGate}
 * (Moderación y Seguridad vs. Soporte Financiero) que cada controller sigue
 * necesitando — es la red que evita que un endpoint nuevo bajo /api/admin/**
 * quede accesible a cualquier usuario autenticado si alguien olvida invocar el
 * gate. Misma tabla, mismo criterio de revocación inmediata que ya usa el gate:
 * no se agrega ningún rol de admin al JWT (SecurityConfig, comentario de
 * cabecera: admins.rol nunca comparte JWT ni autorización con usuarios finales).
 */
@Component
public class AdminActivoAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AdminRepository adminRepository;

    public AdminActivoAuthorizationManager(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        // AUD-027: el nombre del principal es el UUID del usuario.
        UUID usuarioId = null;
        if (auth != null && auth.isAuthenticated()) {
            try {
                usuarioId = UUID.fromString(auth.getName());
            } catch (IllegalArgumentException e) {
                usuarioId = null; // p. ej. anonymousUser
            }
        }
        boolean esAdminActivo = usuarioId != null && adminRepository.findByUsuario_IdAndActivoTrue(usuarioId).isPresent();
        return new AuthorizationDecision(esAdminActivo);
    }
}
