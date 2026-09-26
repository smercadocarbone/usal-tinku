package com.tinku.matching;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-MATCH-012 / FR-ADM-009: temas que la gente busca y el catálogo no cubre, para que el
 * equipo lo actualice (el catálogo es vivo, ADR-M2-04). Liviano a propósito:
 * <ul>
 *   <li>Un contador por texto normalizado (minúsculas, sin tildes, los números largos como
 *       "#"): una fila por tema distinto, no una por búsqueda. Sin usuario, sin fecha por
 *       búsqueda.</li>
 *   <li>Las búsquedas de menores no se registran (Artículo II, minimización).</li>
 *   <li>Tope de {@value #MAXIMO_FILAS} filas: al pasarlo se descartan las menos pedidas.</li>
 *   <li>El Admin solo ve lo pedido al menos {@code tinku.matching.temas-sugeridos.minimo-veces}
 *       veces: temas en general, no búsquedas sueltas.</li>
 * </ul>
 * Nunca hace fallar una búsqueda: si el registro falla, se loguea y listo.
 */
@Service
public class TemasSugeridosService {

    private static final Logger LOG = LoggerFactory.getLogger(TemasSugeridosService.class);
    static final int MAXIMO_FILAS = 500;
    static final int LARGO_MAXIMO = 80;
    private static final int LISTADO_MAXIMO = 50;

    private final JdbcTemplate jdbc;
    private final int minimoVeces;

    public TemasSugeridosService(JdbcTemplate jdbc,
                                 @Value("${tinku.matching.temas-sugeridos.minimo-veces:3}") int minimoVeces) {
        this.jdbc = jdbc;
        this.minimoVeces = minimoVeces;
    }

    public void registrar(Usuario buscador, String texto, Optional<AreaTema> area) {
        if (buscador.getTipo() == TipoUsuario.MENOR) {
            return;
        }
        String normalizado = normalizar(texto);
        if (normalizado.length() < 3) {
            return;
        }
        try {
            jdbc.update("""
                    INSERT INTO matching.temas_sugeridos (texto, veces, nivel, materia, ultima_vez)
                    VALUES (?, 1, ?, ?, now())
                    ON CONFLICT (texto) DO UPDATE
                       SET veces = matching.temas_sugeridos.veces + 1,
                           ultima_vez = now(),
                           nivel = COALESCE(EXCLUDED.nivel, matching.temas_sugeridos.nivel),
                           materia = COALESCE(EXCLUDED.materia, matching.temas_sugeridos.materia)
                    """,
                    normalizado, area.map(AreaTema::nivel).orElse(null), area.map(AreaTema::materia).orElse(null));
            jdbc.update("""
                    DELETE FROM matching.temas_sugeridos
                     WHERE id IN (SELECT id FROM matching.temas_sugeridos
                                   ORDER BY veces DESC, ultima_vez DESC OFFSET ?)
                    """, MAXIMO_FILAS);
        } catch (RuntimeException e) {
            LOG.warn("No se pudo registrar un tema sugerido ({})", e.getClass().getSimpleName());
        }
    }

    /** Lo que ve el Admin: los temas pedidos varias veces, los más pedidos primero. */
    public List<TemaSugerido> listar() {
        return jdbc.query("""
                SELECT id, texto, veces, nivel, materia, ultima_vez
                  FROM matching.temas_sugeridos
                 WHERE veces >= ?
                 ORDER BY veces DESC, ultima_vez DESC
                 LIMIT ?
                """,
                (rs, rowNum) -> new TemaSugerido(
                        rs.getObject("id", UUID.class), rs.getString("texto"), rs.getInt("veces"),
                        rs.getString("nivel"), rs.getString("materia"),
                        rs.getTimestamp("ultima_vez").toInstant()),
                minimoVeces, LISTADO_MAXIMO);
    }

    /** El Admin lo resolvió (agregó el tema al catálogo o lo descartó): deja de aparecer. */
    public boolean descartar(UUID id) {
        return jdbc.update("DELETE FROM matching.temas_sugeridos WHERE id = ?", id) > 0;
    }

    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
        String limpio = sinTildes
                .replaceAll("\\d{4,}", "#")           // DNI, teléfonos: nunca se guardan
                .replaceAll("[^a-z0-9#+\\- ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return limpio.length() > LARGO_MAXIMO ? limpio.substring(0, LARGO_MAXIMO).trim() : limpio;
    }

    public record TemaSugerido(UUID id, String texto, int veces, String nivel, String materia,
                               Instant ultimaVez) {
    }
}
