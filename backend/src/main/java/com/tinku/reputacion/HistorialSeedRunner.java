package com.tinku.reputacion;

import com.tinku.pagos.service.ComisionPlataforma;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Historial de clases SOLO en perfil {@code dev} o {@code seed-demo}, después de Tutores (orden 1)
 * y Estudiantes (orden 2): la reputación de cada Tutor sale de sesiones que
 * realmente dieron los Estudiantes del seed — nunca de calificaciones sueltas.
 *
 * <p>Por cada clase del historial arma la cadena completa que dejaría el flujo
 * real: Reserva {@code finalizada} → Transacción {@code liberado} (bypass) →
 * Sesión {@code finalizada} → calificación pública {@code estudiante_a_tutor}
 * firmada por el beneficiario + calificación oculta {@code tutor_a_estudiante}.
 * La oculta es obligatoria: sin ella el Tutor queda bloqueado para reservas
 * nuevas por FR-REP-006. Al final recalcula {@code senales_implicitas_tutor}.
 *
 * <p>Todo el historial es de hace 3 a ~75 días: ninguna calificación de 1-2
 * estrellas cae en las últimas 24hs (BR-MATCH-01 no sombrea a nadie) y todas
 * están fuera de la ventana de edición de 48hs. Solo participan Estudiantes
 * adultos: el piloto no tiene sesiones con menores (T-TES-10).
 *
 * <p>Se inserta directo por SQL a propósito: el flujo real exige la ventana de
 * 15 min y fechas futuras, así que no hay forma de "reservar en el pasado". No
 * se emiten eventos ni se agendan jobs de Quartz. Idempotente por Tutor
 * (marca: {@code mp_payment_id} con prefijo {@value #PREFIJO_PAGO}).
 */
@Component
@Profile({"dev", "seed-demo"})
@Order(3)
public class HistorialSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HistorialSeedRunner.class);

    private static final String PREFIJO_PAGO = "seed-historial-";
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Duration DURACION = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final ComisionPlataforma comision;

    public HistorialSeedRunner(JdbcTemplate jdbc, ComisionPlataforma comision) {
        this.jdbc = jdbc;
        this.comision = comision;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, UUID> ids = idsPorDni();
        Set<String> ocupados = new HashSet<>();
        int tutores = 0;
        int clases = 0;
        for (int t = 0; t < HISTORIAL.size(); t++) {
            HistorialTutor h = HISTORIAL.get(t);
            UUID tutorId = ids.get(h.tutorDni());
            List<UUID> alumnos = h.alumnosDni().stream().map(ids::get).filter(java.util.Objects::nonNull).toList();
            if (tutorId == null || alumnos.isEmpty() || yaTieneHistorial(tutorId)) {
                continue;
            }
            BigDecimal precio = jdbc.queryForObject(
                    "SELECT precio_sesion FROM pagos.tarifas_tutor WHERE tutor_id = ?", BigDecimal.class, tutorId);
            Random rnd = new Random(h.tutorDni().hashCode());
            int n = h.perfil().min + rnd.nextInt(h.perfil().max - h.perfil().min + 1);
            for (int k = 0; k < n; k++) {
                UUID alumno = alumnos.get(k % alumnos.size());
                Instant horario = horarioLibre(t, k, tutorId, alumno, ocupados);
                insertarClase(tutorId, alumno, precio, horario, h, rnd);
            }
            recalcularSenales(tutorId, h.perfil());
            tutores++;
            clases += n;
        }
        if (tutores > 0) {
            log.info("Seed de historial: {} clases finalizadas y calificadas para {} tutores.", clases, tutores);
        }
    }

    private void insertarClase(UUID tutorId, UUID alumno, BigDecimal precio, Instant horario,
                               HistorialTutor h, Random rnd) {
        Instant inicio = horario.plusSeconds(60L + rnd.nextInt(180));
        Instant fin = horario.plus(DURACION).minusSeconds(rnd.nextInt(240));
        Instant calificadaAt = fin.plus(Duration.ofMinutes(20 + rnd.nextInt(600)));

        UUID reservaId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reservas.reservas (id, pagador_id, beneficiario_id, tutor_id, horario, precio, estado, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'finalizada', ?)
                """, reservaId, alumno, alumno, tutorId, ts(horario), precio,
                ts(horario.minus(Duration.ofHours(24 + rnd.nextInt(120)))));
        jdbc.update("""
                INSERT INTO pagos.transacciones (reserva_id, mp_payment_id, monto_bruto, comision_plataforma,
                                                 estado, liberar_at, en_bypass, created_at)
                VALUES (?, ?, ?, ?, 'liberado', ?, TRUE, ?)
                """, reservaId, PREFIJO_PAGO + reservaId, precio, comision.calcular(precio),
                ts(fin.plus(Duration.ofHours(48))), ts(horario.minus(Duration.ofHours(12))));

        UUID sesionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO aula.sesiones_aprendizaje (id, reserva_id, estado, inicio_real, fin_real,
                        duracion_efectiva_segundos, duracion_agendada_segundos,
                        estudiante_joined_at, tutor_joined_at, created_at, updated_at)
                VALUES (?, ?, 'finalizada', ?, ?, ?, ?, ?, ?, ?, ?)
                """, sesionId, reservaId, ts(inicio), ts(fin),
                (int) Duration.between(inicio, fin).getSeconds(), (int) DURACION.getSeconds(),
                ts(inicio), ts(horario.minusSeconds(rnd.nextInt(120))), ts(horario.minus(Duration.ofHours(12))), ts(fin));

        int estrellas = h.perfil().estrellas(rnd);
        String comentario = rnd.nextInt(10) < 7 ? comentario(estrellas, h.materia(), rnd) : null;
        jdbc.update("""
                INSERT INTO reputacion.calificaciones (sesion_id, autor_id, direccion, estrellas, comentario,
                                                      editable_hasta, created_at)
                VALUES (?, ?, 'estudiante_a_tutor', ?, ?, ?, ?)
                """, sesionId, alumno, estrellas, comentario,
                ts(calificadaAt.plus(Duration.ofHours(48))), ts(calificadaAt));
        Instant ocultaAt = fin.plus(Duration.ofMinutes(5 + rnd.nextInt(120)));
        jdbc.update("""
                INSERT INTO reputacion.calificaciones (sesion_id, autor_id, direccion, estrellas,
                                                      editable_hasta, created_at)
                VALUES (?, ?, 'tutor_a_estudiante', ?, ?, ?)
                """, sesionId, tutorId, 4 + rnd.nextInt(2),
                ts(ocultaAt.plus(Duration.ofHours(48))), ts(ocultaAt));
    }

    /**
     * Día y hora (en punto, dentro de las franjas de 08 a 23) del k-ésimo
     * encuentro. Las restricciones de exclusión de {@code reservas} prohíben
     * dos reservas del mismo Tutor o del mismo beneficiario en el mismo
     * instante (incluidas las que ya estén en la base): si choca, corre una
     * hora; si el día entero choca, pasa al anterior.
     */
    private Instant horarioLibre(int t, int k, UUID tutorId, UUID alumno, Set<String> ocupados) {
        LocalDate dia = LocalDate.now(ZONA).minusDays(3L + k * 5L + (t % 5));
        int hora = 9 + (t * 3 + k * 5) % 13;
        for (int intento = 0; ; intento++) {
            Instant horario = dia.atTime(hora, 0).atZone(ZONA).toInstant();
            String claveTutor = "T" + tutorId + horario;
            String claveAlumno = "A" + alumno + horario;
            if (!ocupados.contains(claveTutor) && !ocupados.contains(claveAlumno)
                    && !ocupadoEnBase(tutorId, alumno, horario)) {
                ocupados.add(claveTutor);
                ocupados.add(claveAlumno);
                return horario;
            }
            hora = hora >= 21 ? 9 : hora + 1;
            if (intento % 13 == 12) {
                dia = dia.minusDays(1);
            }
        }
    }

    private boolean ocupadoEnBase(UUID tutorId, UUID alumno, Instant horario) {
        Boolean ocupado = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM reservas.reservas
                                WHERE (tutor_id = ? OR beneficiario_id = ?)
                                  AND horario = ? AND estado <> 'cancelada')
                """, Boolean.class, tutorId, alumno, ts(horario));
        return Boolean.TRUE.equals(ocupado);
    }

    /** Reconstruye el agregado de señales implícitas a partir del historial insertado. */
    private void recalcularSenales(UUID tutorId, Perfil perfil) {
        jdbc.update("""
                INSERT INTO reputacion.senales_implicitas_tutor (tutor_id, puntualidad_promedio, tasa_recontratacion,
                        tasa_cancelacion_noshow, sesiones_dictadas_total, updated_at)
                SELECT ?, ?, COALESCE(1 - count(DISTINCT beneficiario_id)::numeric / NULLIF(count(*), 0), 0),
                       ?, count(*), now()
                  FROM reservas.reservas WHERE tutor_id = ? AND estado = 'finalizada'
                ON CONFLICT (tutor_id) DO UPDATE SET
                    puntualidad_promedio = EXCLUDED.puntualidad_promedio,
                    tasa_recontratacion = EXCLUDED.tasa_recontratacion,
                    tasa_cancelacion_noshow = EXCLUDED.tasa_cancelacion_noshow,
                    sesiones_dictadas_total = EXCLUDED.sesiones_dictadas_total,
                    updated_at = now()
                """, tutorId, perfil.puntualidad, perfil.cancelaciones, tutorId);
    }

    private boolean yaTieneHistorial(UUID tutorId) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pagos.transacciones tx
                                 JOIN reservas.reservas r ON r.id = tx.reserva_id
                                WHERE r.tutor_id = ? AND tx.mp_payment_id LIKE ?)
                """, Boolean.class, tutorId, PREFIJO_PAGO + "%");
        return Boolean.TRUE.equals(existe);
    }

    private Map<String, UUID> idsPorDni() {
        Set<String> dnis = new HashSet<>();
        HISTORIAL.forEach(h -> {
            dnis.add(h.tutorDni());
            dnis.addAll(h.alumnosDni());
        });
        return jdbc.query("SELECT dni, id::text FROM identidad.usuarios WHERE dni = ANY (?)",
                        ps -> ps.setArray(1, ps.getConnection().createArrayOf("varchar", dnis.toArray())),
                        (rs, i) -> Map.entry(rs.getString(1), UUID.fromString(rs.getString(2))))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }

    // ── Perfiles de reputación ──────────────────────────────────────────────────

    /** Rango de clases dadas + pesos de 1 a 5 estrellas. POCAS queda por debajo
     *  del umbral de 5 (FR-REP-007): el perfil muestra "Sin calificaciones suficientes". */
    private enum Perfil {
        EXCELENTE(9, 14, new int[]{0, 0, 1, 4, 15}, "1.0000", "0.0000"),
        BUENO(6, 10, new int[]{0, 0, 2, 9, 6}, "0.9500", "0.0500"),
        REGULAR(5, 8, new int[]{0, 2, 6, 7, 3}, "0.8500", "0.1500"),
        POCAS(2, 4, new int[]{0, 0, 1, 4, 5}, "1.0000", "0.0000");

        final int min;
        final int max;
        private final int[] pesos;
        final BigDecimal puntualidad;
        final BigDecimal cancelaciones;

        Perfil(int min, int max, int[] pesos, String puntualidad, String cancelaciones) {
            this.min = min;
            this.max = max;
            this.pesos = pesos;
            this.puntualidad = new BigDecimal(puntualidad).setScale(4, RoundingMode.HALF_UP);
            this.cancelaciones = new BigDecimal(cancelaciones).setScale(4, RoundingMode.HALF_UP);
        }

        int estrellas(Random rnd) {
            int total = java.util.Arrays.stream(pesos).sum();
            int x = rnd.nextInt(total);
            for (int i = 0; i < pesos.length; i++) {
                x -= pesos[i];
                if (x < 0) {
                    return i + 1;
                }
            }
            return 5;
        }
    }

    private record HistorialTutor(String tutorDni, Perfil perfil, String materia, List<String> alumnosDni) {
    }

    /** Qué Estudiantes tomaron clase con quién: cada alumno aparece con Tutores
     *  de lo que estudia (ver comentarios de {@code EstudianteSeedRunner}).
     *  Tutores de primario sin historial: sus alumnos serían menores. */
    private static final List<HistorialTutor> HISTORIAL = List.of(
            h("30111222", Perfil.EXCELENTE, "matemática", "40000004", "40000010"),
            h("30222333", Perfil.BUENO, "inglés", "40000009", "40000011"),
            h("30333444", Perfil.EXCELENTE, "física", "40000004", "40000013"),
            h("30444555", Perfil.BUENO, "programación", "40000001", "40000009"),
            h("30555666", Perfil.BUENO, "historia", "40000010", "40000006"),
            h("30666777", Perfil.EXCELENTE, "anatomía", "40000002", "40000011"),
            h("31000001", Perfil.REGULAR, "matemática", "40000010", "40000004"),
            h("31000002", Perfil.EXCELENTE, "matemática", "40000004", "40000013"),
            h("31000003", Perfil.BUENO, "matemática", "40000010", "40000008"),
            h("31000004", Perfil.POCAS, "matemática", "40000010"),
            h("31000005", Perfil.EXCELENTE, "inglés", "40000009", "40000006", "40000011"),
            h("31000006", Perfil.BUENO, "inglés", "40000011", "40000009"),
            h("31000007", Perfil.POCAS, "inglés", "40000009"),
            h("31000008", Perfil.BUENO, "física", "40000004", "40000014"),
            h("31000009", Perfil.EXCELENTE, "química", "40000004", "40000002"),
            h("31000010", Perfil.POCAS, "química", "40000004"),
            h("31000011", Perfil.BUENO, "biología", "40000010", "40000002"),
            h("31000012", Perfil.EXCELENTE, "literatura", "40000010", "40000006"),
            h("31000013", Perfil.REGULAR, "literatura", "40000010", "40000006"),
            h("31000014", Perfil.BUENO, "historia", "40000010", "40000004"),
            h("31000015", Perfil.BUENO, "programación", "40000009", "40000001"),
            h("31000016", Perfil.EXCELENTE, "análisis matemático", "40000013", "40000005", "40000001"),
            h("31000017", Perfil.BUENO, "análisis y estadística", "40000014", "40000007"),
            h("31000018", Perfil.EXCELENTE, "algoritmos", "40000001", "40000009"),
            h("31000019", Perfil.REGULAR, "física", "40000013", "40000014"),
            h("31000020", Perfil.EXCELENTE, "derecho civil", "40000003"),
            h("31000021", Perfil.BUENO, "derecho penal", "40000003"),
            h("31000022", Perfil.BUENO, "contabilidad", "40000005"),
            h("31000023", Perfil.EXCELENTE, "microeconomía", "40000007", "40000005"),
            h("31000024", Perfil.BUENO, "histología", "40000002"),
            h("31000025", Perfil.EXCELENTE, "psicología", "40000006"),
            h("31000026", Perfil.REGULAR, "sistemas de representación", "40000008"),
            h("31000027", Perfil.BUENO, "enfermería", "40000011"));

    private static HistorialTutor h(String tutorDni, Perfil perfil, String materia, String... alumnos) {
        return new HistorialTutor(tutorDni, perfil, materia, List.of(alumnos));
    }

    // ── Comentarios ─────────────────────────────────────────────────────────────

    private static String comentario(int estrellas, String materia, Random rnd) {
        List<String> pool = switch (estrellas) {
            case 5 -> CINCO;
            case 4 -> CUATRO;
            case 3 -> TRES;
            default -> DOS;
        };
        return pool.get(rnd.nextInt(pool.size())).replace("{m}", materia);
    }

    private static final List<String> CINCO = List.of(
            "Explica {m} con una paciencia enorme. Llegué sin entender nada y salí resolviendo los ejercicios por mi cuenta.",
            "Excelente clase. Preparó ejemplos parecidos a los del parcial y me sirvieron muchísimo.",
            "Muy claro y ordenado. Por fin entendí {m} de verdad y no de memoria.",
            "Aprobé el final gracias a estas clases. Súper recomendable.",
            "Se nota que sabe un montón de {m} y además sabe explicarlo. Vuelvo seguro.",
            "Puntual, con el material listo y muy buena onda. Las clases se pasan volando.",
            "Me ayudó a armar un plan de estudio para {m} y lo estoy cumpliendo. Genial.",
            "Respondió todas mis dudas sin apurarse. La mejor clase de {m} que tomé.",
            "Muy buena explicación, con ejercicios graduados de fácil a difícil. Se entiende todo.",
            "Ya es mi tercera clase y cada una suma. Recomiendo mucho.");

    private static final List<String> CUATRO = List.of(
            "Muy buena clase de {m}. Me hubiera gustado hacer algún ejercicio más, pero se entendió bien.",
            "Explica bien y es paciente. A veces va un poco rápido, pero si le pedís repite sin problema.",
            "Buena clase, me sirvió para ordenar los temas de {m} antes del examen.",
            "Clara y prolija. Le sumaría material para practicar después de la clase.",
            "Me destrabó un tema que venía arrastrando. Buena experiencia.",
            "Bien en general. Arrancamos unos minutos tarde pero lo compensó al final.",
            "Conoce mucho de {m}. Las primeras clases me costó seguirle el ritmo.");

    private static final List<String> TRES = List.of(
            "Correcta, aunque esperaba más ejercicios de práctica de {m}.",
            "Sabe del tema pero la explicación fue algo desordenada.",
            "Estuvo bien, pero me quedaron dudas que no llegamos a ver.",
            "La clase fue muy teórica; me hubiera servido más práctica.",
            "Cumplió, sin más. Para repasar {m} está bien.");

    private static final List<String> DOS = List.of(
            "No llegamos a ver lo que le había pedido de {m}.",
            "Se cortó varias veces la conexión y la clase quedó a medias.",
            "Explicó muy rápido y no me dio lugar a preguntar.");
}
