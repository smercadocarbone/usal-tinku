package com.tinku.matching.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.matching.web.BusquedaResponse;
import com.tinku.shared.evaluacion.EvaluacionFlag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * T-TES-11b §4.1 (I-01): registra cada búsqueda con sus resultados en orden y la "aceptación" de
 * una sugerencia (abrir el perfil o reservar un Tutor de la lista).
 *
 * <p>Minimización (Art. V): del texto libre solo se guarda la longitud. Art. II: las búsquedas de
 * una cuenta Menor no se registran. Todo apagado con {@link EvaluacionFlag} en {@code false}.</p>
 */
@Service
public class RegistroBusquedasEvaluacion {

    public enum TipoAceptacion { PERFIL, RESERVA }

    private final JdbcTemplate jdbc;
    private final EvaluacionFlag flag;

    public RegistroBusquedasEvaluacion(JdbcTemplate jdbc, EvaluacionFlag flag) {
        this.jdbc = jdbc;
        this.flag = flag;
    }

    /** Id de la búsqueda registrada, o vacío si el flag está apagado o quien busca es un Menor. */
    @Transactional
    public Optional<UUID> registrar(Usuario usuario, String texto, List<BusquedaResponse> resultados) {
        if (!flag.habilitada() || usuario.getTipo() == TipoUsuario.MENOR) {
            return Optional.empty();
        }
        UUID[] ids = resultados.stream().map(BusquedaResponse::tutorId).toArray(UUID[]::new);
        UUID id = jdbc.execute((java.sql.Connection con) -> {
            Array arreglo = con.createArrayOf("uuid", ids);
            try (var ps = con.prepareStatement("""
                    INSERT INTO matching.evaluacion_busquedas (usuario_id, longitud_texto, cantidad_resultados, tutor_ids)
                    VALUES (?, ?, ?, ?) RETURNING id""")) {
                ps.setObject(1, usuario.getId());
                ps.setInt(2, texto == null ? 0 : texto.length());
                ps.setInt(3, ids.length);
                ps.setArray(4, arreglo);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            }
        });
        return Optional.of(id);
    }

    /**
     * Registra que {@code usuarioId} aceptó a {@code tutorId} de su búsqueda {@code busquedaId}.
     * Idempotente por (búsqueda, tutor, tipo). La posición se toma de lo que se mostró.
     */
    @Transactional
    public void aceptar(UUID usuarioId, UUID busquedaId, UUID tutorId, String tipo) {
        flag.exigir();
        List<Integer> posiciones = jdbc.queryForList("""
                SELECT COALESCE(array_position(tutor_ids, ?::uuid), 0)
                  FROM matching.evaluacion_busquedas WHERE id = ? AND usuario_id = ?""",
                Integer.class, tutorId, busquedaId, usuarioId);
        if (posiciones.isEmpty()) {
            throw new BusquedaEvaluacionNoEncontradaException();
        }
        TipoAceptacion t = parse(tipo);
        int posicion = posiciones.getFirst();
        if (posicion == 0) {
            throw new AceptacionInvalidaException("Ese tutor no estaba entre los resultados de la búsqueda.");
        }
        jdbc.update("""
                INSERT INTO matching.evaluacion_aceptaciones (busqueda_id, tutor_id, posicion, tipo)
                VALUES (?, ?, ?, ?) ON CONFLICT ON CONSTRAINT uq_evaluacion_aceptacion DO NOTHING""",
                busquedaId, tutorId, posicion, t.name());
    }

    private static TipoAceptacion parse(String tipo) {
        try {
            return TipoAceptacion.valueOf(tipo);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AceptacionInvalidaException("El tipo tiene que ser PERFIL o RESERVA.");
        }
    }
}
