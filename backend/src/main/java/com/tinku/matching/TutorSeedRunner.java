package com.tinku.matching;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Semilla de Tutores SOLO en perfil {@code dev} (mismo patrón que
 * {@code AdminSeedRunner}): crea usuarios TUTOR con credencial dev y les asigna
 * los {@code tema_ids} que conocen en {@code matching.perfiles_tutor_matching}.
 *
 * <p>Idempotente (skipea DNI ya registrados) y loguea al final el resumen con
 * las credenciales de prueba. No toca credenciales académicas ni CAP: el tutor
 * queda {@code activo_para_matching=true} directo para poder probar la selección
 * de temas (contrato 2b) y el ranking de /match sin pasar por el flujo de M1.
 *
 * <p>Los temas se resuelven por clave (nivel, anio_o_carrera, materia, nombre)
 * contra el catálogo de V20 (los ids son {@code gen_random_uuid}, nada debe
 * asumir valores fijos).
 */
@Component
@Profile("dev")
public class TutorSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TutorSeedRunner.class);

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;
    private final PerfilTutorTemasRepository perfilRepo;
    private final JdbcTemplate jdbc;
    private final Almacenamiento almacenamiento;

    public TutorSeedRunner(UsuarioRepository usuarioRepo,
                           PasswordEncoder passwordEncoder,
                           PerfilTutorTemasRepository perfilRepo,
                           JdbcTemplate jdbc,
                           Almacenamiento almacenamiento) {
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
        this.perfilRepo = perfilRepo;
        this.jdbc = jdbc;
        this.almacenamiento = almacenamiento;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<UUID> creados = new ArrayList<>();
        for (TutorSeed tutor : TUTORES) {
            if (usuarioRepo.existsByDni(tutor.dni())) {
                continue;
            }
            Usuario usuario = new Usuario();
            usuario.setDni(tutor.dni());
            usuario.setNombre(tutor.nombre());
            usuario.setApellido(tutor.apellido());
            usuario.setEmail(tutor.email());
            usuario.setFechaNacimiento(tutor.fechaNacimiento());
            usuario.setTipo(TipoUsuario.TUTOR);
            usuario.setPasswordHash(passwordEncoder.encode(PASSWORD_DEV));
            usuario.setEstadoCuenta(EstadoCuenta.ACTIVA);
            usuario.setActivoParaMatching(true);
            // FR-ID-028: la foto es obligatoria para aparecer en búsquedas; en dev, un avatar liso.
            usuario.setFotoRef(almacenamiento.guardar(avatar(creados.size()), "avatar-seed.png"));
            usuarioRepo.saveAndFlush(usuario);

            List<UUID> temaIds = resolverTemas(tutor.temas());
            perfilRepo.upsertTemaIds(usuario.getId(), temaIds);
            jdbc.update("UPDATE matching.perfiles_tutor_matching SET activo_para_matching = TRUE WHERE tutor_id = ?",
                    usuario.getId());
            creados.add(usuario.getId());
        }
        if (!creados.isEmpty()) {
            log.info("Seed de tutores: {} creados. Login con DNI + '{}' ({})",
                    creados.size(), PASSWORD_DEV, PORTAL_DEBUG_MENSAJE);
        }
    }

    /** Resuelve los UUID de temas del catálogo (sin asumir ids). */
    private List<UUID> resolverTemas(List<TemaRef> temas) {
        List<UUID> ids = new ArrayList<>();
        for (TemaRef tema : temas) {
            List<UUID> filas = jdbc.query("""
                            SELECT t.id::text
                              FROM matching.temas t
                              JOIN matching.trayectos tr ON tr.id = t.trayecto_id
                             WHERE tr.nivel = ? AND tr.anio_o_carrera = ?
                               AND tr.materia = ? AND t.nombre = ?
                            """,
                    (rs, rowNum) -> UUID.fromString(rs.getString(1)),
                    tema.nivel(), tema.curso(), tema.materia(), tema.nombre());
            if (filas.isEmpty()) {
                log.warn("Seed de tutores: no existe el tema '{}' para {} / {} / {}; se omitió.",
                        tema.nombre(), tema.nivel(), tema.curso(), tema.materia());
            } else {
                ids.add(filas.getFirst());
            }
        }
        return ids;
    }

    static final String PASSWORD_DEV = "password123";
    private static final String PORTAL_DEBUG_MENSAJE = "valores de prueba, solo local";

    private record TemaRef(String nivel, String curso, String materia, String nombre) {
    }

    private record TutorSeed(String dni, String nombre, String apellido, String email,
                             LocalDate fechaNacimiento, List<TemaRef> temas) {
    }

    private static final List<TutorSeed> TUTORES = List.of(
            new TutorSeed("30111222", "María", "Pérez", "maria.perez@test.tinku",
                    LocalDate.of(1995, 3, 12), List.of(
                            tema("secundario", "4°", "Matemática", "Números Reales y radicales"),
                            tema("secundario", "4°", "Matemática", "Funciones"),
                            tema("secundario", "4°", "Matemática", "Función cuadrática"),
                            tema("secundario", "4°", "Matemática", "Sistemas de ecuaciones"),
                            tema("secundario", "4°", "Matemática", "Estadística y probabilidad"),
                            tema("primario", "1°", "Matemática", "Números"),
                            tema("primario", "1°", "Matemática", "Sumas y restas"))),
            new TutorSeed("30222333", "Juan", "García", "juan.garcia@test.tinku",
                    LocalDate.of(1992, 7, 1), List.of(
                            tema("primario", "1°", "Inglés", "Greetings and introductions"),
                            tema("primario", "1°", "Inglés", "The alphabet"),
                            tema("primario", "1°", "Inglés", "Numbers"),
                            tema("primario", "1°", "Inglés", "Colors"),
                            tema("secundario", "4°", "Inglés", "Past simple vs present perfect"),
                            tema("secundario", "4°", "Inglés", "Reported speech"),
                            tema("secundario", "4°", "Inglés", "Passive voice"))),
            new TutorSeed("30333444", "Ana", "Rodríguez", "ana.rodriguez@test.tinku",
                    LocalDate.of(1989, 11, 25), List.of(
                            tema("secundario", "4°", "Física", "Cinemática"),
                            tema("secundario", "4°", "Física", "Dinámica"),
                            tema("secundario", "4°", "Física", "Trabajo y energía"),
                            tema("secundario", "4°", "Física", "Calor y temperatura"),
                            tema("secundario", "4°", "Química", "El átomo y el modelo actual"),
                            tema("secundario", "4°", "Química", "La tabla periódica"))),
            new TutorSeed("30444555", "Carlos", "López", "carlos.lopez@test.tinku",
                    LocalDate.of(1998, 2, 19), List.of(
                            tema("universitario", "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Fundamentos de la programación"),
                            tema("universitario", "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Estructuras de control"),
                            tema("universitario", "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Funciones y modularización"),
                            tema("universitario", "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Listas, pilas y colas"),
                            tema("universitario", "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Recursividad"),
                            tema("universitario", "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Búsqueda y ordenamiento"))),
            new TutorSeed("30555666", "Laura", "Martínez", "laura.martinez@test.tinku",
                    LocalDate.of(1994, 9, 3), List.of(
                            tema("secundario", "4°", "Historia", "Revolución Industrial"),
                            tema("secundario", "4°", "Historia", "Revolución Francesa"),
                            tema("secundario", "4°", "Historia", "Las independencias americanas"),
                            tema("secundario", "4°", "Geografía", "Población mundial"),
                            tema("secundario", "4°", "Geografía", "Desarrollo y desigualdad"),
                            tema("secundario", "4°", "Geografía", "Geopolítica mundial"))),
            new TutorSeed("30666777", "Diego", "Fernández", "diego.fernandez@test.tinku",
                    LocalDate.of(1991, 5, 30), List.of(
                            tema("universitario", "Medicina", "Anatomía", "Miología"),
                            tema("universitario", "Medicina", "Anatomía", "Sistema cardiovascular"),
                            tema("universitario", "Medicina", "Anatomía", "Sistema nervioso"),
                            tema("universitario", "Medicina", "Fisiología y Biofísica", "Homeostasis y medio interno"),
                            tema("universitario", "Medicina", "Fisiología y Biofísica", "Fisiología cardiovascular"))),
            new TutorSeed("30777888", "Sofía", "Gómez", "sofia.gomez@test.tinku",
                    LocalDate.of(2000, 8, 15), List.of(
                            tema("primario", "2°", "Matemática", "Sumas y restas con canjes"),
                            tema("primario", "2°", "Matemática", "La multiplicación"),
                            tema("primario", "3°", "Matemática", "La división"),
                            tema("primario", "3°", "Matemática", "Fracciones"),
                            tema("primario", "3°", "Lengua", "Textos narrativos"),
                            tema("primario", "3°", "Lengua", "Comprensión lectora"))));

    private static TemaRef tema(String nivel, String curso, String materia, String nombre) {
        return new TemaRef(nivel, curso, materia, nombre);
    }

    /** PNG liso de 96x96 (solo dev): distinto color por tutor para distinguirlos. */
    private static byte[] avatar(int n) {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.getHSBColor((n * 0.137f) % 1f, 0.45f, 0.85f));
        g.fillRect(0, 0, 96, 96);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el avatar de seed", e);
        }
    }
}
