package com.tinku.matching;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Semilla de Tutores SOLO en perfil {@code dev} o {@code seed-demo} (mismo patrón que
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
 * asumir valores fijos). Con {@code nombre == null} se toman TODOS los temas de
 * ese trayecto — así varios Tutores comparten materia y la búsqueda devuelve
 * más de un resultado por temática.
 *
 * <p>Además deja a cada Tutor del seed reservable: tarifa (M5, solo si no tiene)
 * y franjas semanales de 1 hora de 08 a 23 todos los días (M4, solo si no tiene
 * ninguna). Las franjas son de 1 hora porque la duración de la Sesión ES la de
 * la franja que cubre el horario (FR-RES-023, {@code duracionFranjaQueCubre}).
 *
 * <p>Los embeddings NO se calculan acá (los calcula el servicio Python): después
 * del primer arranque hay que llamar a {@code POST /recompute-embeddings}.
 */
@Component
@Profile({"dev", "seed-demo"})
@Order(1)
public class TutorSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TutorSeedRunner.class);

    private static final LocalTime PRIMERA_FRANJA = LocalTime.of(8, 0);
    private static final LocalTime FIN_ULTIMA_FRANJA = LocalTime.of(23, 0);

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;
    private final PerfilTutorTemasRepository perfilRepo;
    private final JdbcTemplate jdbc;

    public TutorSeedRunner(UsuarioRepository usuarioRepo,
                           PasswordEncoder passwordEncoder,
                           PerfilTutorTemasRepository perfilRepo,
                           JdbcTemplate jdbc) {
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
        this.perfilRepo = perfilRepo;
        this.jdbc = jdbc;
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
            usuarioRepo.saveAndFlush(usuario);

            List<UUID> temaIds = resolverTemas(tutor.temas());
            perfilRepo.upsertTemaIds(usuario.getId(), temaIds);
            jdbc.update("UPDATE matching.perfiles_tutor_matching SET activo_para_matching = TRUE WHERE tutor_id = ?",
                    usuario.getId());
            creados.add(usuario.getId());
        }

        int reservables = 0;
        for (TutorSeed tutor : TUTORES) {
            UUID tutorId = usuarioRepo.findByDni(tutor.dni()).map(Usuario::getId).orElse(null);
            if (tutorId != null && dejarReservable(tutorId, BigDecimal.valueOf(tutor.tarifa()))) {
                reservables++;
            }
        }

        if (!creados.isEmpty() || reservables > 0) {
            log.info("Seed de tutores: {} creados, {} con tarifa/franjas nuevas. Login con DNI + '{}' ({}). "
                            + "Recordá POST /recompute-embeddings en el servicio de matching.",
                    creados.size(), reservables, PASSWORD_DEV, PORTAL_DEBUG_MENSAJE);
        }
    }

    /** Tarifa + franjas semanales de 1h; no pisa lo que el Tutor ya haya cargado. */
    private boolean dejarReservable(UUID tutorId, BigDecimal tarifa) {
        int tarifas = jdbc.update("""
                        INSERT INTO pagos.tarifas_tutor (tutor_id, precio_sesion)
                        VALUES (?, ?) ON CONFLICT (tutor_id) DO NOTHING
                        """, tutorId, tarifa);
        Integer franjas = jdbc.queryForObject(
                "SELECT count(*) FROM reservas.franjas_disponibilidad WHERE tutor_id = ?",
                Integer.class, tutorId);
        if (franjas != null && franjas > 0) {
            return tarifas > 0;
        }
        List<Object[]> filas = new ArrayList<>();
        for (short dia = 0; dia <= 6; dia++) {
            for (LocalTime h = PRIMERA_FRANJA; h.isBefore(FIN_ULTIMA_FRANJA); h = h.plusHours(1)) {
                filas.add(new Object[]{tutorId, dia, h, h.plusHours(1)});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO reservas.franjas_disponibilidad (tutor_id, dia_semana, hora_inicio, hora_fin)
                VALUES (?, ?, ?, ?)
                """, filas);
        return true;
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
                               AND tr.materia = ? AND (?::text IS NULL OR t.nombre = ?)
                             ORDER BY t.orden
                            """,
                    (rs, rowNum) -> UUID.fromString(rs.getString(1)),
                    tema.nivel(), tema.curso(), tema.materia(), tema.nombre(), tema.nombre());
            if (filas.isEmpty()) {
                log.warn("Seed de tutores: no existe el tema '{}' para {} / {} / {}; se omitió.",
                        tema.nombre() == null ? "(todos)" : tema.nombre(),
                        tema.nivel(), tema.curso(), tema.materia());
            } else if (tema.nombre() == null) {
                ids.addAll(filas);
            } else {
                ids.add(filas.getFirst());
            }
        }
        return ids;
    }

    static final String PASSWORD_DEV = "password123";
    private static final String PORTAL_DEBUG_MENSAJE = "valores de prueba, solo local";

    private static final String SEC = "secundario";
    private static final String PRI = "primario";
    private static final String UNI = "universitario";

    private record TemaRef(String nivel, String curso, String materia, String nombre) {
    }

    private record TutorSeed(String dni, String nombre, String apellido, String email,
                             LocalDate fechaNacimiento, int tarifa, List<TemaRef> temas) {
    }

    private static final List<TutorSeed> TUTORES = List.of(
            new TutorSeed("30111222", "María", "Pérez", "maria.perez@test.tinku",
                    LocalDate.of(1995, 3, 12), 15000, List.of(
                            tema(SEC, "4°", "Matemática", "Números Reales y radicales"),
                            tema(SEC, "4°", "Matemática", "Funciones"),
                            tema(SEC, "4°", "Matemática", "Función cuadrática"),
                            tema(SEC, "4°", "Matemática", "Sistemas de ecuaciones"),
                            tema(SEC, "4°", "Matemática", "Estadística y probabilidad"),
                            tema(PRI, "1°", "Matemática", "Números"),
                            tema(PRI, "1°", "Matemática", "Sumas y restas"))),
            new TutorSeed("30222333", "Juan", "García", "juan.garcia@test.tinku",
                    LocalDate.of(1992, 7, 1), 14000, List.of(
                            tema(PRI, "1°", "Inglés", "Greetings and introductions"),
                            tema(PRI, "1°", "Inglés", "The alphabet"),
                            tema(PRI, "1°", "Inglés", "Numbers"),
                            tema(PRI, "1°", "Inglés", "Colors"),
                            tema(SEC, "4°", "Inglés", "Past simple vs present perfect"),
                            tema(SEC, "4°", "Inglés", "Reported speech"),
                            tema(SEC, "4°", "Inglés", "Passive voice"))),
            new TutorSeed("30333444", "Ana", "Rodríguez", "ana.rodriguez@test.tinku",
                    LocalDate.of(1989, 11, 25), 16000, List.of(
                            tema(SEC, "4°", "Física", "Cinemática"),
                            tema(SEC, "4°", "Física", "Dinámica"),
                            tema(SEC, "4°", "Física", "Trabajo y energía"),
                            tema(SEC, "4°", "Física", "Calor y temperatura"),
                            tema(SEC, "4°", "Química", "El átomo y el modelo actual"),
                            tema(SEC, "4°", "Química", "La tabla periódica"))),
            new TutorSeed("30444555", "Carlos", "López", "carlos.lopez@test.tinku",
                    LocalDate.of(1998, 2, 19), 20000, List.of(
                            tema(UNI, "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Fundamentos de la programación"),
                            tema(UNI, "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Estructuras de control"),
                            tema(UNI, "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Funciones y modularización"),
                            tema(UNI, "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Listas, pilas y colas"),
                            tema(UNI, "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Recursividad"),
                            tema(UNI, "Ingeniería en Sistemas de Información", "Algoritmos y Estructuras de Datos", "Búsqueda y ordenamiento"))),
            new TutorSeed("30555666", "Laura", "Martínez", "laura.martinez@test.tinku",
                    LocalDate.of(1994, 9, 3), 13000, List.of(
                            tema(SEC, "4°", "Historia", "Revolución Industrial"),
                            tema(SEC, "4°", "Historia", "Revolución Francesa"),
                            tema(SEC, "4°", "Historia", "Las independencias americanas"),
                            tema(SEC, "4°", "Geografía", "Población mundial"),
                            tema(SEC, "4°", "Geografía", "Desarrollo y desigualdad"),
                            tema(SEC, "4°", "Geografía", "Geopolítica mundial"))),
            new TutorSeed("30666777", "Diego", "Fernández", "diego.fernandez@test.tinku",
                    LocalDate.of(1991, 5, 30), 24000, List.of(
                            tema(UNI, "Medicina", "Anatomía", "Miología"),
                            tema(UNI, "Medicina", "Anatomía", "Sistema cardiovascular"),
                            tema(UNI, "Medicina", "Anatomía", "Sistema nervioso"),
                            tema(UNI, "Medicina", "Fisiología y Biofísica", "Homeostasis y medio interno"),
                            tema(UNI, "Medicina", "Fisiología y Biofísica", "Fisiología cardiovascular"))),
            new TutorSeed("30777888", "Sofía", "Gómez", "sofia.gomez@test.tinku",
                    LocalDate.of(2000, 8, 15), 11000, List.of(
                            tema(PRI, "2°", "Matemática", "Sumas y restas con canjes"),
                            tema(PRI, "2°", "Matemática", "La multiplicación"),
                            tema(PRI, "3°", "Matemática", "La división"),
                            tema(PRI, "3°", "Matemática", "Fracciones"),
                            tema(PRI, "3°", "Lengua", "Textos narrativos"),
                            tema(PRI, "3°", "Lengua", "Comprensión lectora"))),

            // ── Secundario: varias opciones por materia ─────────────────────────
            new TutorSeed("31000001", "Martín", "Suárez", "martin.suarez@test.tinku",
                    LocalDate.of(1996, 4, 8), 12000,
                    trayecto(SEC, "Matemática", "1°", "2°", "3°")),
            new TutorSeed("31000002", "Valentina", "Acosta", "valentina.acosta@test.tinku",
                    LocalDate.of(1993, 10, 22), 17000, concat(
                            trayecto(SEC, "Matemática", "4°", "5°", "6°"),
                            trayecto(SEC, "Física", "5°", "6°"))),
            new TutorSeed("31000003", "Nicolás", "Benítez", "nicolas.benitez@test.tinku",
                    LocalDate.of(1990, 1, 14), 15000,
                    trayecto(SEC, "Matemática", "3°", "4°", "5°", "6°")),
            new TutorSeed("31000004", "Camila", "Romero", "camila.romero@test.tinku",
                    LocalDate.of(2001, 6, 2), 10000, concat(
                            trayecto(SEC, "Matemática", "1°", "2°"),
                            trayecto(PRI, "Matemática", "5°", "6°"))),
            new TutorSeed("31000005", "Lucas", "Díaz", "lucas.diaz@test.tinku",
                    LocalDate.of(1988, 12, 5), 16000,
                    trayecto(SEC, "Inglés", "1°", "2°", "3°", "4°", "5°", "6°")),
            new TutorSeed("31000006", "Julieta", "Morales", "julieta.morales@test.tinku",
                    LocalDate.of(1997, 3, 29), 13000, concat(
                            trayecto(PRI, "Inglés", "4°", "5°", "6°"),
                            trayecto(SEC, "Inglés", "1°", "2°", "3°"))),
            new TutorSeed("31000007", "Emma", "Sosa", "emma.sosa@test.tinku",
                    LocalDate.of(1999, 9, 17), 12500,
                    trayecto(SEC, "Inglés", "4°", "5°", "6°")),
            new TutorSeed("31000008", "Tomás", "Herrera", "tomas.herrera@test.tinku",
                    LocalDate.of(1992, 2, 11), 15500,
                    trayecto(SEC, "Física", "3°", "4°", "5°", "6°")),
            new TutorSeed("31000009", "Florencia", "Castro", "florencia.castro@test.tinku",
                    LocalDate.of(1994, 7, 25), 15500, concat(
                            trayecto(SEC, "Química", "3°", "4°", "5°", "6°"),
                            trayecto(SEC, "Biología", "3°", "4°"))),
            new TutorSeed("31000010", "Agustín", "Ruiz", "agustin.ruiz@test.tinku",
                    LocalDate.of(2000, 11, 3), 11500, concat(
                            trayecto(SEC, "Química", "3°", "4°", "5°", "6°"),
                            trayecto(SEC, "Física", "3°", "4°"))),
            new TutorSeed("31000011", "Micaela", "Torres", "micaela.torres@test.tinku",
                    LocalDate.of(1995, 5, 19), 14000,
                    trayecto(SEC, "Biología", "3°", "4°", "5°", "6°")),
            new TutorSeed("31000012", "Paula", "Giménez", "paula.gimenez@test.tinku",
                    LocalDate.of(1987, 8, 30), 14500,
                    trayecto(SEC, "Lengua y Literatura", "1°", "2°", "3°", "4°", "5°", "6°")),
            new TutorSeed("31000013", "Federico", "Álvarez", "federico.alvarez@test.tinku",
                    LocalDate.of(1991, 12, 12), 13500, concat(
                            trayecto(SEC, "Lengua y Literatura", "4°", "5°", "6°"),
                            trayecto(SEC, "Filosofía", "6°"))),
            new TutorSeed("31000014", "Rocío", "Molina", "rocio.molina@test.tinku",
                    LocalDate.of(1996, 1, 27), 13000, concat(
                            trayecto(SEC, "Historia", "3°", "4°", "5°"),
                            trayecto(SEC, "Geografía", "3°", "4°", "5°"))),
            new TutorSeed("31000015", "Matías", "Ortiz", "matias.ortiz@test.tinku",
                    LocalDate.of(1998, 4, 4), 16500, concat(
                            trayecto(SEC, "Programación", "4°"),
                            trayecto(SEC, "Programación II", "5°"),
                            trayecto(SEC, "Programación Avanzada", "6°"),
                            trayecto(SEC, "Bases de Datos", "5°"))),

            // ── Universitario ───────────────────────────────────────────────────
            new TutorSeed("31000016", "Gonzalo", "Silva", "gonzalo.silva@test.tinku",
                    LocalDate.of(1990, 3, 3), 21000, concat(
                            trayecto(UNI, "Análisis Matemático I", "Ingeniería Civil", "Contador Público"),
                            trayecto(UNI, "Álgebra y Geometría Analítica", "Ingeniería Civil"))),
            new TutorSeed("31000017", "Carolina", "Medina", "carolina.medina@test.tinku",
                    LocalDate.of(1993, 6, 21), 20000, concat(
                            trayecto(UNI, "Análisis Matemático I", "Licenciatura en Economía"),
                            trayecto(UNI, "Análisis Matemático II", "Ingeniería Industrial"),
                            trayecto(UNI, "Probabilidad y Estadística", "Ingeniería Industrial"))),
            new TutorSeed("31000018", "Sebastián", "Rojas", "sebastian.rojas@test.tinku",
                    LocalDate.of(1995, 10, 9), 22000, concat(
                            trayecto(UNI, "Matemática Discreta", "Ingeniería en Sistemas de Información"),
                            trayecto(UNI, "Algoritmos y Estructuras de Datos", "Ingeniería en Sistemas de Información"),
                            trayecto(UNI, "Arquitectura de Computadoras", "Ingeniería en Sistemas de Información"))),
            new TutorSeed("31000019", "Luciana", "Vega", "luciana.vega@test.tinku",
                    LocalDate.of(1992, 9, 14), 19000, concat(
                            trayecto(UNI, "Física I", "Ingeniería Civil"),
                            trayecto(UNI, "Física II", "Ingeniería Industrial"),
                            trayecto(UNI, "Química General", "Ingeniería Civil"))),
            new TutorSeed("31000020", "Ignacio", "Pereyra", "ignacio.pereyra@test.tinku",
                    LocalDate.of(1986, 2, 6), 25000, concat(
                            trayecto(UNI, "Elementos de Derecho Civil (Parte General)", "Abogacía"),
                            trayecto(UNI, "Elementos de Derecho Constitucional", "Abogacía"),
                            trayecto(UNI, "Obligaciones Civiles y Comerciales", "Abogacía"))),
            new TutorSeed("31000021", "Antonella", "Figueroa", "antonella.figueroa@test.tinku",
                    LocalDate.of(1994, 11, 28), 21000, concat(
                            trayecto(UNI, "Elementos de Derecho Penal y de Procesal Penal", "Abogacía"),
                            trayecto(UNI, "Elementos de Derecho Constitucional", "Abogacía"))),
            new TutorSeed("31000022", "Bruno", "Cabrera", "bruno.cabrera@test.tinku",
                    LocalDate.of(1989, 7, 7), 19500, concat(
                            trayecto(UNI, "Teoría Contable", "Contador Público", "Licenciatura en Administración de Empresas"),
                            trayecto(UNI, "Economía", "Contador Público"),
                            trayecto(UNI, "Administración General", "Contador Público"))),
            new TutorSeed("31000023", "Victoria", "Ledesma", "victoria.ledesma@test.tinku",
                    LocalDate.of(1991, 4, 16), 20500, concat(
                            trayecto(UNI, "Microeconomía I", "Licenciatura en Economía"),
                            trayecto(UNI, "Macroeconomía I", "Licenciatura en Economía"),
                            trayecto(UNI, "Estadística I", "Licenciatura en Economía"))),
            new TutorSeed("31000024", "Joaquín", "Navarro", "joaquin.navarro@test.tinku",
                    LocalDate.of(1990, 8, 2), 24000, concat(
                            trayecto(UNI, "Anatomía", "Medicina"),
                            trayecto(UNI, "Histología, Biología Celular, Embriología y Genética", "Medicina"),
                            trayecto(UNI, "Química Biológica", "Medicina"))),
            new TutorSeed("31000025", "Milagros", "Ríos", "milagros.rios@test.tinku",
                    LocalDate.of(1993, 1, 31), 18000, concat(
                            trayecto(UNI, "Procesos Psicológicos Básicos", "Licenciatura en Psicología"),
                            trayecto(UNI, "Neurofisiología", "Licenciatura en Psicología"),
                            trayecto(UNI, "Historia de la Psicología", "Licenciatura en Psicología"),
                            trayecto(UNI, "Metodología de la Investigación", "Licenciatura en Psicología"))),
            new TutorSeed("31000026", "Ezequiel", "Domínguez", "ezequiel.dominguez@test.tinku",
                    LocalDate.of(1988, 5, 23), 19000, concat(
                            trayecto(UNI, "Matemática", "Arquitectura"),
                            trayecto(UNI, "Sistemas de Representación Geométrica", "Arquitectura"),
                            trayecto(UNI, "Introducción al Conocimiento Proyectual", "Arquitectura"))),
            new TutorSeed("31000027", "Daniela", "Quiroga", "daniela.quiroga@test.tinku",
                    LocalDate.of(1985, 10, 10), 17000, concat(
                            trayecto(UNI, "Estructura y Función del Cuerpo Humano", "Licenciatura en Enfermería"),
                            trayecto(UNI, "Introducción a la Microbiología y Parasitología", "Licenciatura en Enfermería"),
                            trayecto(UNI, "Fundamentos, Prácticas y Tendencias en Enfermería", "Licenciatura en Enfermería"))),

            // ── Primario ────────────────────────────────────────────────────────
            new TutorSeed("31000028", "Lorena", "Aguirre", "lorena.aguirre@test.tinku",
                    LocalDate.of(1984, 12, 19), 10500, concat(
                            trayecto(PRI, "Matemática", "1°", "2°", "3°", "4°", "5°", "6°"),
                            trayecto(PRI, "Lengua", "1°", "2°", "3°"))),
            new TutorSeed("31000029", "Pablo", "Villalba", "pablo.villalba@test.tinku",
                    LocalDate.of(1990, 6, 6), 10000, concat(
                            trayecto(PRI, "Ciencias Naturales", "4°", "5°", "6°"),
                            trayecto(PRI, "Ciencias Sociales", "4°", "5°", "6°"))),
            new TutorSeed("31000030", "Natalia", "Cáceres", "natalia.caceres@test.tinku",
                    LocalDate.of(1996, 2, 24), 10500, concat(
                            trayecto(PRI, "Lengua", "4°", "5°", "6°"),
                            trayecto(PRI, "Inglés", "1°", "2°", "3°"))));

    private static TemaRef tema(String nivel, String curso, String materia, String nombre) {
        return new TemaRef(nivel, curso, materia, nombre);
    }

    /** Todos los temas de la materia en cada año (o carrera, en universitario). */
    private static List<TemaRef> trayecto(String nivel, String materia, String... cursos) {
        return Arrays.stream(cursos).map(c -> new TemaRef(nivel, c, materia, null)).toList();
    }

    @SafeVarargs
    private static List<TemaRef> concat(List<TemaRef>... partes) {
        return Arrays.stream(partes).flatMap(List::stream).toList();
    }
}
