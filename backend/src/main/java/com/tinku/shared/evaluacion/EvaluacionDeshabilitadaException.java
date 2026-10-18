package com.tinku.shared.evaluacion;

/** T-TES-11b: la instrumentación de la evaluación está apagada → 404. */
public class EvaluacionDeshabilitadaException extends RuntimeException {
    public EvaluacionDeshabilitadaException() {
        super("No encontramos lo que buscabas.");
    }
}
