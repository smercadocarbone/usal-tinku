package com.tinku.identidad;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Semilla de Estudiantes SOLO en perfil {@code dev} o {@code seed-demo}, después de los Tutores
 * ({@code TutorSeedRunner}, orden 1): adultos con capacidad de Estudiante,
 * Adultos Responsables y sus menores (con consentimiento y Tutores
 * autorizados), para probar búsqueda, reserva y las restricciones del
 * Artículo II sin pasar por el registro con OCR.
 *
 * <p>Los menores quedan creados pero NO pueden reservar mientras
 * {@code tinku.menores.sesiones-habilitadas=false} (T-TES-10): sirven para
 * probar justamente ese corte fail-closed y el flujo de Solicitud.
 *
 * <p>Idempotente: skipea DNIs ya registrados y autorizaciones existentes.
 */
@Component
@Profile({"dev", "seed-demo"})
@Order(2)
public class EstudianteSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EstudianteSeedRunner.class);

    static final String PASSWORD_DEV = "password123";
    private static final String VERSION_CONSENTIMIENTO = "v1.0";

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public EstudianteSeedRunner(UsuarioRepository usuarioRepo,
                                PasswordEncoder passwordEncoder,
                                JdbcTemplate jdbc) {
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int creados = 0;
        for (AdultoSeed a : ADULTOS) {
            if (usuarioRepo.existsByDni(a.dni())) {
                continue;
            }
            Usuario u = base(a.dni(), a.nombre(), a.apellido(), a.fechaNacimiento());
            u.setTipo(TipoUsuario.ADULTO);
            u.setEmail(a.email());
            u.setCapacidadEstudiante(a.estudiante());
            u.setCapacidadAdultoResponsable(a.adultoResponsable());
            usuarioRepo.saveAndFlush(u);
            creados++;
        }
        for (MenorSeed m : MENORES) {
            if (usuarioRepo.existsByDni(m.dni())) {
                continue;
            }
            Usuario ar = usuarioRepo.findByDni(m.adultoResponsableDni()).orElse(null);
            if (ar == null) {
                log.warn("Seed de estudiantes: no existe el Adulto Responsable {}; se omitió el menor {}.",
                        m.adultoResponsableDni(), m.dni());
                continue;
            }
            Usuario menor = base(m.dni(), m.nombre(), m.apellido(), m.fechaNacimiento());
            menor.setTipo(TipoUsuario.MENOR);
            menor.setAdultoResponsable(ar);
            usuarioRepo.saveAndFlush(menor);
            jdbc.update("""
                    INSERT INTO identidad.consentimientos_menor (menor_id, adulto_responsable_id, version_texto)
                    VALUES (?, ?, ?)
                    """, menor.getId(), ar.getId(), VERSION_CONSENTIMIENTO);
            for (String tutorDni : m.tutoresAutorizados()) {
                usuarioRepo.findByDni(tutorDni).ifPresent(tutor -> jdbc.update("""
                        INSERT INTO identidad.autorizaciones_tutor (adulto_responsable_id, menor_id, tutor_id)
                        VALUES (?, ?, ?) ON CONFLICT ON CONSTRAINT uq_autorizacion DO NOTHING
                        """, ar.getId(), menor.getId(), tutor.getId()));
            }
            creados++;
        }
        if (creados > 0) {
            log.info("Seed de estudiantes: {} usuarios creados. Login con DNI + '{}' (valores de prueba, solo local)",
                    creados, PASSWORD_DEV);
        }
    }

    private Usuario base(String dni, String nombre, String apellido, LocalDate fechaNacimiento) {
        Usuario u = new Usuario();
        u.setDni(dni);
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setFechaNacimiento(fechaNacimiento);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD_DEV));
        u.setEstadoCuenta(EstadoCuenta.ACTIVA);
        return u;
    }

    private record AdultoSeed(String dni, String nombre, String apellido, String email,
                              LocalDate fechaNacimiento, boolean estudiante, boolean adultoResponsable) {
    }

    private record MenorSeed(String dni, String nombre, String apellido, LocalDate fechaNacimiento,
                             String adultoResponsableDni, List<String> tutoresAutorizados) {
    }

    /** Los comentarios de {@code HistorialSeedRunner} (M7) los firman estos adultos. */
    private static final List<AdultoSeed> ADULTOS = List.of(
            estudiante("40000001", "Lautaro", "Ferreyra", LocalDate.of(2003, 5, 14)),   // Ing. en Sistemas
            estudiante("40000002", "Abril", "Peralta", LocalDate.of(2004, 2, 3)),       // Medicina
            estudiante("40000003", "Bautista", "Correa", LocalDate.of(2002, 9, 21)),    // Abogacía
            estudiante("40000004", "Catalina", "Luna", LocalDate.of(2007, 6, 30)),      // último año del secundario
            estudiante("40000005", "Facundo", "Maldonado", LocalDate.of(2001, 11, 8)),  // Contador Público
            estudiante("40000006", "Delfina", "Paz", LocalDate.of(2006, 1, 17)),        // Psicología
            estudiante("40000007", "Thiago", "Ibáñez", LocalDate.of(2003, 3, 25)),      // Economía
            estudiante("40000008", "Renata", "Godoy", LocalDate.of(2004, 8, 12)),       // Arquitectura
            estudiante("40000009", "Santino", "Vera", LocalDate.of(1998, 12, 1)),       // adulto: inglés y programación
            estudiante("40000010", "Martina", "Sánchez", LocalDate.of(2007, 4, 9)),     // terminando el secundario
            estudiante("40000013", "Joaquina", "Bustos", LocalDate.of(2005, 7, 19)),    // Ingeniería Civil
            estudiante("40000014", "Iván", "Mansilla", LocalDate.of(2004, 10, 27)),     // Ingeniería Industrial
            new AdultoSeed("40000011", "Emilia", "Ponce", "emilia.ponce@test.tinku",
                    LocalDate.of(1985, 3, 2), true, true),                              // estudia Enfermería + AR
            new AdultoSeed("40000012", "Hernán", "Arias", "hernan.arias@test.tinku",
                    LocalDate.of(1980, 7, 15), false, true));                           // solo Adulto Responsable

    private static final List<MenorSeed> MENORES = List.of(
            new MenorSeed("50000001", "Mía", "Arias", LocalDate.of(2016, 5, 10), "40000012",
                    List.of("30777888", "31000028")),
            new MenorSeed("50000002", "Benjamín", "Arias", LocalDate.of(2011, 9, 3), "40000012",
                    List.of("30111222", "31000005")),
            new MenorSeed("50000003", "Olivia", "Ponce", LocalDate.of(2015, 1, 22), "40000011",
                    List.of("30777888", "31000030")));

    private static AdultoSeed estudiante(String dni, String nombre, String apellido, LocalDate nacimiento) {
        String email = (sinTildes(nombre) + "." + sinTildes(apellido) + "@test.tinku").toLowerCase();
        return new AdultoSeed(dni, nombre, apellido, email, nacimiento, true, false);
    }

    private static String sinTildes(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
