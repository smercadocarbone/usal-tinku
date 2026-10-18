package com.tinku.admin.evaluacion;

/** T-TES-11b: una respuesta por usuario → 409. */
public class NpsYaRespondidoException extends RuntimeException {
    public NpsYaRespondidoException() {
        super("Ya respondiste esta pregunta. ¡Gracias!");
    }
}
