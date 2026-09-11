package com.tinku.identidad.dto;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoCredencial;

import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de una credencial (US-4). No expone datos internos del revisor.
 */
public record CredencialResponse(
        UUID id,
        TipoCredencial tipoDocumento,
        EstadoCredencial estado,
        int numeroIntento,
        Instant createdAt
) {

    public static CredencialResponse from(CredencialAcademica c) {
        return new CredencialResponse(
                c.getId(), c.getTipoDocumento(), c.getEstado(),
                c.getNumeroIntento(), c.getCreatedAt());
    }
}
