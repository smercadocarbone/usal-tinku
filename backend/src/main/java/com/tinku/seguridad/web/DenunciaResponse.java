package com.tinku.seguridad.web;

import com.tinku.seguridad.model.Denuncia;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.model.MotivoDenuncia;

import java.time.Instant;
import java.util.UUID;

/** Vista pública de una Denuncia (anónima para el denunciado — FR-SEC-006: la
 * identidad del denunciante nunca viaja en la respuesta). */
public record DenunciaResponse(
        UUID id,
        UUID denunciadoId,
        EstadoDenuncia estado,
        MotivoDenuncia motivo,
        UUID sesionId,
        String descargoTexto,
        Instant descargoVenceAt,
        Instant slaResolucionVenceAt,
        boolean prioridadAlta,
        Instant resueltaAt,
        Instant createdAt) {

    public static DenunciaResponse from(Denuncia d) {
        return new DenunciaResponse(d.getId(), d.getDenunciadoId(), d.getEstado(), d.getMotivo(),
                d.getSesionId(), d.getDescargoTexto(), d.getDescargoVenceAt(),
                d.getSlaResolucionVenceAt(), d.isPrioridadAlta(), d.getResueltaAt(), d.getCreatedAt());
    }
}