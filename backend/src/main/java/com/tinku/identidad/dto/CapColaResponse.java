package com.tinku.identidad.dto;

import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fila de la cola de moderación del CAP (T03 §2.4): lo mínimo para revisar, sin DNI. */
public record CapColaResponse(
        UUID id,
        UUID tutorId,
        String tutorNombre,
        String tutorApellido,
        EstadoCap estado,
        LocalDate fechaEmision,
        LocalDate venceAt,
        String categoriaAntecedente,
        int numeroIntento,
        Instant createdAt
) {
    public static CapColaResponse from(CertificadoAntecedentesPenales c) {
        return new CapColaResponse(c.getId(), c.getTutor().getId(), c.getTutor().getNombre(),
                c.getTutor().getApellido(), c.getEstado(), c.getFechaEmision(), c.getVenceAt(),
                c.getCategoriaAntecedente(), c.getNumeroIntento(), c.getCreatedAt());
    }
}
