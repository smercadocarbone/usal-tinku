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
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
                                           RequestAuthorizationContext context) {
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

    /**
     * R-F F-8 (T4-H12): admin activo CON ese rol, en el filter chain. Así el rol se evalúa antes que
     * la validación del cuerpo (antes, un cuerpo vacío con el rol equivocado daba 400 y mostraba el
     * esquema). El gate de servicio ({@code AdminModeracionGate}) sigue igual: defensa en profundidad.
     */
    public AuthorizationManager<RequestAuthorizationContext> conRol(com.tinku.admin.model.RolAdmin rol) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            UUID usuarioId = null;
            if (auth != null && auth.isAuthenticated()) {
                try {
                    usuarioId = UUID.fromString(auth.getName());
                } catch (IllegalArgumentException e) {
                    usuarioId = null;
                }
            }
            return new AuthorizationDecision(usuarioId != null
                    && adminRepository.findByUsuario_IdAndRolAndActivoTrue(usuarioId, rol).isPresent());
        };
    }
}
