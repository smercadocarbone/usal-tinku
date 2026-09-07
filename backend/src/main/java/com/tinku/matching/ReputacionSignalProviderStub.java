package com.tinku.matching;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stub del puerto {@link ReputacionSignalProvider} para el Chunk M2-C: M7
 * todavía no existe como módulo (ver docs/Tasks_Tinku_Chunks.md, nota del
 * Chunk M2-C). Hasta que el Chunk M7-D lo reemplace por la implementación real
 * que consulta reputación, este devuelve "sin señales" y "sin sombra" — el
 * ranking queda puro por similitud semántica, que es lo correcto para el piloto.
 */
@Primary
@Component
public class ReputacionSignalProviderStub implements ReputacionSignalProvider {

    @Override
    public Map<UUID, Double> senalesImplicitas(Collection<UUID> tutorIds) {
        return Map.of();
    }

    @Override
    public Set<UUID> tutoresEnSombraBrMatch01(Collection<UUID> tutorIds) {
        return Set.of();
    }
}