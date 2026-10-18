package com.tinku.admin.evaluacion;

/** T-TES-11b: "desde" posterior a "hasta" → 422. */
public class IndicadoresPeriodoInvalidoException extends RuntimeException {
    public IndicadoresPeriodoInvalidoException() {
        super("La fecha de inicio tiene que ser anterior o igual a la de fin.");
    }
}
