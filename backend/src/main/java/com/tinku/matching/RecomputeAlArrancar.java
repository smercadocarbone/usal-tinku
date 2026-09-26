package com.tinku.matching;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, pide un recompute en segundo plano: así el catálogo entero queda embebido
 * (ADR-M2-03/04, lo usa la recomendación por área) sin esperar a que un Tutor cambie sus
 * temas. Si no hay nada pendiente, el servicio Python solo relee los perfiles. Fuera de
 * {@code test}: ahí el cliente es un mock que los tests verifican.
 */
@Component
@Profile("!test")
public class RecomputeAlArrancar {

    private final RecomputeEmbeddingsDisparador disparador;

    public RecomputeAlArrancar(RecomputeEmbeddingsDisparador disparador) {
        this.disparador = disparador;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        disparador.dispararDespuesDelCommit();
    }
}
