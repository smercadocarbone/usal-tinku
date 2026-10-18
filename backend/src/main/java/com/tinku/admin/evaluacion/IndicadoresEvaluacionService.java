package com.tinku.admin.evaluacion;

import com.tinku.shared.evaluacion.EvaluacionFlag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * T-TES-11b §4.3: cálculo de I-01, NPS (I-04), I-02 y búsquedas sin resultado para el panel de Admin.
 * Solo lectura y agregado (ningún dato por usuario sale de acá). Lee tablas de {@code matching} y
 * {@code reputacion} sin reglas de negocio de esos módulos.
 */
@Service
public class IndicadoresEvaluacionService {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant SIN_INICIO = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant SIN_FIN = Instant.parse("3000-01-01T00:00:00Z");
    private static final int PROMOTOR_DESDE = 9;
    private static final int DETRACTOR_HASTA = 6;

    private final JdbcTemplate jdbc;
    private final EvaluacionFlag flag;
    private final int topK;

    public IndicadoresEvaluacionService(JdbcTemplate jdbc, EvaluacionFlag flag,
                                        @Value("${tinku.evaluacion.top-k:5}") int topK) {
        this.jdbc = jdbc;
        this.flag = flag;
        this.topK = topK;
    }

    public IndicadoresEvaluacionResponse calcular(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IndicadoresPeriodoInvalidoException();
        }
        Timestamp ini = Timestamp.from(desde == null ? SIN_INICIO : desde.atStartOfDay(ZONA).toInstant());
        Timestamp fin = Timestamp.from(hasta == null ? SIN_FIN : hasta.plusDays(1).atStartOfDay(ZONA).toInstant());

        Map<String, Object> b = jdbc.queryForMap("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE cantidad_resultados > 0) AS con_resultado,
                       count(*) FILTER (WHERE cantidad_resultados = 0) AS sin_resultado,
                       count(*) FILTER (WHERE cantidad_resultados > 0 AND EXISTS (
                           SELECT 1 FROM matching.evaluacion_aceptaciones a
                            WHERE a.busqueda_id = eb.id AND a.posicion <= ?)) AS con_aceptacion
                  FROM matching.evaluacion_busquedas eb
                 WHERE eb.created_at >= ? AND eb.created_at < ?""", topK, ini, fin);
        long conResultado = num(b, "con_resultado");
        long conAceptacion = num(b, "con_aceptacion");

        Map<String, Object> tipos = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE a.tipo = 'PERFIL') AS perfil,
                       count(*) FILTER (WHERE a.tipo = 'RESERVA') AS reserva
                  FROM matching.evaluacion_aceptaciones a
                  JOIN matching.evaluacion_busquedas eb ON eb.id = a.busqueda_id
                 WHERE eb.created_at >= ? AND eb.created_at < ?""", ini, fin);
        List<IndicadoresEvaluacionResponse.PorPosicion> porPosicion = jdbc.query("""
                SELECT a.posicion, count(DISTINCT a.busqueda_id) AS cantidad
                  FROM matching.evaluacion_aceptaciones a
                  JOIN matching.evaluacion_busquedas eb ON eb.id = a.busqueda_id
                 WHERE eb.created_at >= ? AND eb.created_at < ?
                 GROUP BY a.posicion ORDER BY a.posicion""",
                (rs, i) -> new IndicadoresEvaluacionResponse.PorPosicion(rs.getInt(1), rs.getLong(2)), ini, fin);

        Map<String, Object> n = jdbc.queryForMap("""
                SELECT count(*) AS respuestas,
                       count(*) FILTER (WHERE puntaje >= ?) AS promotores,
                       count(*) FILTER (WHERE puntaje <= ?) AS detractores
                  FROM admin.nps_respuestas WHERE created_at >= ? AND created_at < ?""",
                PROMOTOR_DESDE, DETRACTOR_HASTA, ini, fin);
        long respuestas = num(n, "respuestas");
        long promotores = num(n, "promotores");
        long detractores = num(n, "detractores");

        Map<String, Object> c = jdbc.queryForMap("""
                SELECT count(*) AS cantidad, avg(estrellas)::float8 AS promedio
                  FROM reputacion.calificaciones
                 WHERE direccion = 'estudiante_a_tutor' AND created_at >= ? AND created_at < ?""", ini, fin);

        return new IndicadoresEvaluacionResponse(
                flag.habilitada(), desde, hasta, topK,
                new IndicadoresEvaluacionResponse.Busquedas(num(b, "total"), conResultado, num(b, "sin_resultado")),
                new IndicadoresEvaluacionResponse.I01(conAceptacion,
                        conResultado == 0 ? null : (double) conAceptacion / conResultado,
                        num(tipos, "perfil"), num(tipos, "reserva"), porPosicion),
                new IndicadoresEvaluacionResponse.Nps(respuestas, promotores, respuestas - promotores - detractores,
                        detractores, respuestas == 0 ? null : 100.0 * (promotores - detractores) / respuestas),
                new IndicadoresEvaluacionResponse.I02(num(c, "cantidad"),
                        c.get("promedio") == null ? null : ((Number) c.get("promedio")).doubleValue()));
    }

    private static long num(Map<String, Object> fila, String clave) {
        Object v = fila.get(clave);
        return v == null ? 0 : ((Number) v).longValue();
    }
}
