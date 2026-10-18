package com.tinku.shared.evaluacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * T-TES-11b (docs/superpowers/specs/tesis/T11b-evaluacion-sin-piloto.md §4): único árbitro de si la
 * instrumentación de las sesiones moderadas está encendida. Default {@code false}: no se registran
 * búsquedas, no se ofrece el NPS y los endpoints de usuario responden 404. Lo enciende el dueño en el
 * entorno de la demo durante las sesiones (env {@code TINKU_EVALUACION_HABILITADA}); no va encendido
 * en la configuración persistente de producción.
 */
@Component
public class EvaluacionFlag {

    private final boolean habilitada;

    public EvaluacionFlag(@Value("${tinku.evaluacion.habilitada:false}") boolean habilitada) {
        this.habilitada = habilitada;
    }

    public boolean habilitada() {
        return habilitada;
    }

    /** Para los endpoints de usuario: con el flag apagado, 404 (como si no existieran). */
    public void exigir() {
        if (!habilitada) {
            throw new EvaluacionDeshabilitadaException();
        }
    }
}
