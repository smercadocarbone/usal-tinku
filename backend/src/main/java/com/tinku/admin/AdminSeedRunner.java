package com.tinku.admin;

import com.tinku.admin.model.Admin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Semilla de admins SOLO en perfil {@code dev}. Reemplaza al allowlist
 * {@code tinku.admin.moderacion.ids} (que el gate ya no lee en runtime): con la
 * tabla {@code admins} en pie, el único rol del allowlist es poblar esa tabla al
 * arrancar. Misma sintaxis, sin [ ] [+:] — {@code Set.of(x.split(","))}.
 *
 * Los DNIs deben corresponder a usuarios YA registrados por el flujo normal de
 * M1 (el seed nunca inventa cuentas); los que no existan se skipean con un
 * warning, no fallan el arranque. Idempotente: no duplica filas.
 */
@Component
@Profile("dev")
public class AdminSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final AdminRepository adminRepo;
    private final UsuarioRepository usuarioRepo;
    private final Set<String> dniModeracion;
    private final Set<String> dniSoporte;

    public AdminSeedRunner(AdminRepository adminRepo,
                           UsuarioRepository usuarioRepo,
                           @Value("${tinku.admin.moderacion.ids:}") String dniModeracion,
                           @Value("${tinku.admin.financiero.ids:}") String dniSoporte) {
        this.adminRepo = adminRepo;
        this.usuarioRepo = usuarioRepo;
        this.dniModeracion = parse(dniModeracion);
        this.dniSoporte = parse(dniSoporte);
    }

    @Override
    public void run(String... args) {
        seedear(dniModeracion, RolAdmin.MODERACION_SEGURIDAD);
        seedear(dniSoporte, RolAdmin.SOPORTE_FINANCIERO);
    }

    private void seedear(Set<String> dnis, RolAdmin rol) {
        dnis.forEach(dni -> usuarioRepo.findByDni(dni).ifPresentOrElse(u -> {
            adminRepo.findByUsuario_DniAndActivoTrue(dni).ifPresentOrElse(
                    ya -> {
                        if (ya.getRol() != rol) {
                            log.warn("El usuario {} ya es admin con rol {}; no se reasigna a {}.",
                                    dni, ya.getRol(), rol);
                        }
                    },
                    () -> {
                        Admin admin = new Admin();
                        admin.setUsuario(u);
                        admin.setRol(rol);
                        adminRepo.save(admin);
                        log.info("Admin {} creado en dev (rol {})", dni, rol.getValor());
                    });
        }, () -> log.warn("Seed de admin: el DNI {} no es un usuario registrado; se ignoró.", dni)));
    }

    private static Set<String> parse(String csv) {
        return csv == null || csv.isBlank()
                ? Set.of()
                : Set.of(csv.split("\\s*,\\s*"));
    }
}