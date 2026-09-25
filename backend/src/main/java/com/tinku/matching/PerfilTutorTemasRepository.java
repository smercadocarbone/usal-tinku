package com.tinku.matching;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a {@code tema_ids UUID[]} de {@code matching.perfiles_tutor_matching}
 * (contrato 2a): la columna no tiene mapeo JPA de primera clase, así que leer y
 * escribir el array con JdbcTemplate + {@code createArrayOf("uuid", ...)} es el
 * camino recomendado por el plan. No toca {@code materias_niveles_ids} (legacy,
 * V7) ni {@code embedding} — ambos los maneja el resto del sistema.
 *
 *  - {@code upsertTemaIds}: el PUT solo persiste los ids; NO toca {@code
 *    embedding} (se repuebla por el recompute de 2c) ni {@code
 *    activo_para_matching}. La fila puede no existir -> INSERT ON CONFLICT.
 *  - {@code acotarCandidatos}: filtro por nombre de tema y/o materia que se
 *    aplica EN JAVA, después del contexto de autorización y antes de /match
 *    (Plan_M2_Temas.md, contrato 2b paso 2).
 */
@Repository
public class PerfilTutorTemasRepository {

    private final JdbcTemplate jdbc;

    public PerfilTutorTemasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lee los {@code tema_ids} del Tutor. Fila inexistente -> lista vacía
     * (el GET de un Tutor sin perfil devuelve []; no se crea la fila). */
    public List<UUID> findTemaIds(UUID tutorId) {
        List<List<UUID>> filas = jdbc.query(
                "SELECT tema_ids FROM matching.perfiles_tutor_matching WHERE tutor_id = ?",
                (rs, rowNum) -> Arrays.stream((Object[]) rs.getArray("tema_ids").getArray())
                        .map(o -> UUID.fromString(o.toString()))
                        .toList(),
                tutorId);
        return filas.isEmpty() ? List.of() : filas.getFirst();
    }

    /** Upsert de la fila del perfil de matching: solo actualiza {@code tema_ids}. */
    public void upsertTemaIds(UUID tutorId, List<UUID> temaIds) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO matching.perfiles_tutor_matching (tutor_id, tema_ids)
                    VALUES (?, ?)
                    ON CONFLICT (tutor_id) DO UPDATE SET tema_ids = EXCLUDED.tema_ids
                    """);
            ps.setObject(1, tutorId);
            ps.setArray(2, con.createArrayOf("uuid", temaIds.toArray()));
            return ps;
        });
    }

    /**
     * Acotamiento de candidatos (contrato 2b paso 2, siempre en Java antes de
     * /match): cruza {@code perfiles_tutor_matching.tema_ids} con {@code temas}
     * y {@code trayectos}. {@code materia} → igualdad exacta del trayecto;
     * {@code nombre} → {@code public.unaccent} + ILIKE (parcial, tolerante a
     * tildes y case-insensitive). {@code public.} calificado porque unaccent se
     * instaló en public y la app corre con search_path identidad.
     */
    public List<UUID> acotarCandidatos(List<UUID> candidatos, String nombre, String materia) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT ptm.tutor_id::uuid
                FROM matching.perfiles_tutor_matching ptm
                JOIN matching.temas t     ON t.id = ANY(ptm.tema_ids)
                JOIN matching.trayectos tr ON tr.id = t.trayecto_id
                WHERE ptm.tutor_id = ANY(?)
                """);
        if (materia != null) {
            sql.append(" AND tr.materia = ?");
        }
        if (nombre != null) {
            sql.append(" AND public.unaccent(t.nombre) ILIKE public.unaccent('%' || ? || '%')");
        }
        String sentencia = sql.toString();
        return jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(sentencia);
            int i = 1;
            ps.setArray(i++, con.createArrayOf("uuid", candidatos.toArray()));
            if (materia != null) {
                ps.setString(i++, materia);
            }
            if (nombre != null) {
                ps.setString(i++, nombre);
            }
            return ps;
        }, (rs, rowNum) -> rs.getObject("tutor_id", UUID.class));
    }

    /** (nivel, materia) de los temas elegidos, en el orden en que el Tutor los eligió
     *  (primera aparición de cada trayecto en {@code tema_ids}). Sin fila o sin temas → []. */
    public List<String[]> nivelYMateriaDeTemas(UUID tutorId) {
        return jdbc.query("""
                SELECT tr.nivel, tr.materia, MIN(array_position(p.tema_ids, t.id)) AS pos
                  FROM matching.perfiles_tutor_matching p
                  JOIN matching.temas t ON t.id = ANY(p.tema_ids)
                  JOIN matching.trayectos tr ON tr.id = t.trayecto_id
                 WHERE p.tutor_id = ?
                 GROUP BY tr.nivel, tr.materia
                 ORDER BY pos
                """,
                (rs, rowNum) -> new String[]{rs.getString("nivel"), rs.getString("materia")},
                tutorId);
    }
}
