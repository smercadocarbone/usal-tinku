package com.tinku.identidad.port;

import com.tinku.identidad.dto.ReputacionTutor;

import java.util.UUID;

/**
 * Puerto hacia la reputación pública del Tutor (M7). El promedio de
 * calificación que devuelve debe respetar FR-REP-007: {@code null} cuando
 * {@code cantidadCalificaciones < 5}. STUB: M7 aún no existe — la
 * implementación real reemplaza {@link ReputacionPerfilProviderStub} cuando el
 * módulo de reputación aterrice (mismo patrón que ReputacionSignalProvider).
 */
public interface ReputacionPerfilProvider {

    ReputacionTutor reputacion(UUID tutorId);
}