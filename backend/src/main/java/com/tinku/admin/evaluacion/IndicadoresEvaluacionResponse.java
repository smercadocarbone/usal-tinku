package com.tinku.admin.evaluacion;

import java.time.LocalDate;
import java.util.List;

/**
 * T-TES-11b §4.3: indicadores agregados de las sesiones moderadas. Los cocientes son {@code null}
 * cuando el denominador es 0 (no hay dato, que no es lo mismo que 0 %).
 */
public record IndicadoresEvaluacionResponse(
        boolean habilitada,
        LocalDate desde,
        LocalDate hasta,
        int topK,
        Busquedas busquedas,
        I01 i01,
        Nps nps,
        I02 i02) {

    public record Busquedas(long total, long conResultado, long sinResultado) {
    }

    /** {@code tasa} = búsquedas con resultado y al menos una aceptación en el top-k / búsquedas con resultado. */
    public record I01(long busquedasConAceptacion, Double tasa, long aceptacionesPerfil, long aceptacionesReserva,
                      List<PorPosicion> porPosicion) {
    }

    public record PorPosicion(int posicion, long cantidad) {
    }

    /** {@code valor} = % promotores (9–10) − % detractores (0–6). */
    public record Nps(long respuestas, long promotores, long pasivos, long detractores, Double valor) {
    }

    /** Calificaciones {@code estudiante_a_tutor} del período (M7). */
    public record I02(long calificaciones, Double promedio) {
    }
}
