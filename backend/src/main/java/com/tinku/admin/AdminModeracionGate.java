package com.tinku.admin;

import com.tinku.shared.AccesoModeracionDenegadoException;

import com.tinku.admin.model.Admin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.identidad.model.TipoUsuario;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gate de autorización por rol de Admin para endpoints del panel de
 * administración (los consumen M7 — calificaciones ocultas —, M9 — resolución
 * de Denuncias/Alertas — y M8 — colas y Soporte Financiero).
 *
 * T-M8-06: reemplaza el allowlist de DNI ({@code tinku.admin.moderacion.ids})
 * por la tabla {@code admin.admins} (V16). El principal del JWT lleva el DNI
 * (UsuarioDetailsService), así que el gate busca la fila de {@code admins}
 * cruzando por {@code identidad.usuarios.dni} y FILTRA por rol + {@code activo}.
 * La allowlist queda solo como semilla de dev (CommandLineRunner, perfil dev);
 * en runtime ya no se lee.
 *
 * Fail-closed: sin fila (o desactivada, o rol incorrecto) → 403 con
 * {@link AccesoModeracionDenegadoException}. Devuelve el UUID del USUARIO
 * asociado (no el id de {@code admins}) porque los consumidores lo persisten en
 * columnas FK a {@code identidad.usuarios} (p.ej. {@code sanciones.admin_id},
 * {@code denuncias.admin_resolutor_id}).
 */
@Component
public class AdminModeracionGate {

    private final AdminRepository adminRepository;

    public AdminModeracionGate(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    /**
     * API histórica de M7/M9 (sin cambios): Admin de Moderación y Seguridad →
     * UUID del {@code Usuario}, o 403.
     */
    public UUID requiereModeracion(Authentication authentication) {
        return requiereAdmin(authentication, RolAdmin.MODERACION_SEGURIDAD).getUsuario().getId();
    }

    /** Admin de Soporte Financiero → UUID del {@code Usuario}, o 403. */
    public UUID requiereSoporteFinanciero(Authentication authentication) {
        return requiereAdmin(authentication, RolAdmin.SOPORTE_FINANCIERO).getUsuario().getId();
    }

    /** Chequeo de rol genérico para los endpoints de M8. */
    public UUID requiereRol(Authentication authentication, RolAdmin rol) {
        return requiereAdmin(authentication, rol).getUsuario().getId();
    }

    /**
     * Admin activo del rol pedido → la fila de {@code admin.admins} (necesaria
     * para atribuir auditoría por {@code admins.id}) o 403.
     */
    public Admin requiereAdmin(Authentication authentication, RolAdmin rol) {
        return nuncaMenor(adminRepository.findByUsuario_IdAndRolAndActivoTrue(usuarioId(authentication), rol)
                .orElseThrow(AccesoModeracionDenegadoException::new));
    }

    /**
     * Admin activo de CUALQUIER rol → la fila de {@code admin.admins}, o 403.
     * Lo usa la auditoría (T-M8-02) y la cola de tickets (transversal a ambos
     * roles, filtrada después por el rol propio del Admin).
     */
    public Admin adminAutenticado(Authentication authentication) {
        return nuncaMenor(adminRepository.findByUsuario_IdAndActivoTrue(usuarioId(authentication))
                .orElseThrow(AccesoModeracionDenegadoException::new));
    }

    /**
     * Conflicto de interés (revisión por rol, R1): un Admin nunca resuelve un caso en el que él
     * mismo es parte (su credencial, su CAP, una Denuncia o Alerta que lo involucra, una
     * transacción de una Reserva suya). Un Tutor puede ser además Admin; esto lo cubre. → 403.
     */
    public static void exigirNoEsParteDelCaso(UUID adminUsuarioId, UUID... involucrados) {
        for (UUID involucrado : involucrados) {
            if (adminUsuarioId.equals(involucrado)) {
                throw new AccesoModeracionDenegadoException();
            }
        }
    }

    /** Art. II: una cuenta de Menor nunca opera el panel, aunque alguien le cargue una fila en admins. */
    private Admin nuncaMenor(Admin admin) {
        if (adminRepository.existsByUsuario_IdAndUsuario_Tipo(admin.getUsuario().getId(), TipoUsuario.MENOR)) {
            throw new AccesoModeracionDenegadoException();
        }
        return admin;
    }

    /** AUD-027: el principal es el UUID; sin sesión o con un nombre que no lo es → 403. */
    private static UUID usuarioId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new AccesoModeracionDenegadoException();
        }
    }
}