package com.tinku.identidad.dto;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoCredencial;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de una credencial (US-4). No expone datos internos del revisor.
 *
 * {@code tieneAprobada} (B12): true si el Tutor tiene ALGUNA credencial
 * aprobada aunque la última siga en revisión — es lo que el banner de
 * /cuenta necesita para no decirle "en revisión" a un Tutor ya verificado.
 * En el flujo de moderación de M8 (una credencial puntual) vale false.
 */
public record CredencialResponse(
        UUID id,
        TipoCredencial tipoDocumento,
        EstadoCredencial estado,
        int numeroIntento,
        Instant createdAt,
        boolean tieneAprobada
) {

    public static CredencialResponse from(CredencialAcademica c, boolean tieneAprobada) {
        return new CredencialResponse(
                c.getId(), c.getTipoDocumento(), c.getEstado(),
                c.getNumeroIntento(), c.getCreatedAt(), tieneAprobada);
    }

    public static CredencialResponse from(CredencialAcademica c) {
        return from(c, false);
    }
}
