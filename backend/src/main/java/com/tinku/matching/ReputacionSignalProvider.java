package com.tinku.matching;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto hacia M7 (Reputación) que M2-C usa para el ajuste final del ranking
 * (FR-MATCH-003) y la sombra temporal de BR-MATCH-01. El reordenamiento vive en
 * Java, no en el servicio Python, porque son reglas de negocio, no de similitud
 * semántica (Plan_M2, sección 3, paso 6).
 *
 * Aún no hay un consumidor real: Chunk M2-D del roadmap reemplaza el
 * {@link ReputacionSignalProviderStub} por una implementación que consulta M7.
 */
public interface ReputacionSignalProvider {

    /**
     * Señales implícitas por Tutor (FR-MATCH-003 — sesiones dictadas, constancia,
     * etc. recopiladas por M7). El valor es un peso de 0.0+ que se suma al score
     * semántico; la ponderación exacta es ADR-M2-02.
     */
    Map<UUID, Double> senalesImplicitas(Collection<UUID> tutorIds);

    /**
     * Sombra de BR-MATCH-01: Tutores con calificación de 1-2 estrellas en las
     * últimas 24hs (M7) que quedan EXCLUIDOS del ranking mientras dure la sombra.
     */
    Set<UUID> tutoresEnSombraBrMatch01(Collection<UUID> tutorIds);
}