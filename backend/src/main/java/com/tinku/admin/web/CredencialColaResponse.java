package com.tinku.admin.web;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoCredencial;

import java.time.Instant;
import java.util.UUID;

// FIXME AUD-007 (auditoría 2026-09-21): el javadoc dice que "la revisión visual del archivo
// es del frontend interno". Ese frontend interno NO existe, y tampoco existe ningún endpoint
// que sirva el archivo. Hoy la Credencial Académica —único mecanismo de confianza vigente
// tras ADR-M1-02— se aprueba a ciegas. Se corrige en FASE 1.
/**
 * Fila de la cola de Credenciales pendientes (US-1, FR-ADM-001) — lo que el
 * Admin necesita para decidir: de quién es, qué documento, en qué intento está
 * y el plazo de la ventana de 48hs ({@code cicloEsperaHasta}). No se expone el
 * {@code archivoUrl} del documento: la revisión visual del archivo es del
 * frontend interno, esto es la cola de decisión (minimización de datos).
 * El repo de la cola hace {@code join fetch} del tutor (open-in-view: false).
 */
public record CredencialColaResponse(
        UUID id,
        UUID tutorId,
        String tutorNombre,
        String tutorApellido,
        TipoCredencial tipoDocumento,
        EstadoCredencial estado,
        int numeroIntento,
        Instant cicloEsperaHasta,
        Instant createdAt) {

    public static CredencialColaResponse from(CredencialAcademica c) {
        return new CredencialColaResponse(
                c.getId(), c.getTutor().getId(), c.getTutor().getNombre(),
                c.getTutor().getApellido(), c.getTipoDocumento(), c.getEstado(),
                c.getNumeroIntento(), c.getCicloEsperaHasta(), c.getCreatedAt());
    }
}