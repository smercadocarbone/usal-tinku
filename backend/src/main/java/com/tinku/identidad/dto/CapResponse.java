package com.tinku.identidad.dto;

import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta de un CAP. No expone datos internos del revisor.
 */
public record CapResponse(
        UUID id,
        EstadoCap estado,
        boolean tieneAntecedentes,
        LocalDate venceAt,
        int numeroIntento
) {
    public static CapResponse from(CertificadoAntecedentesPenales c) {
        return new CapResponse(c.getId(), c.getEstado(), c.isTieneAntecedentes(),
                c.getVenceAt(), c.getNumeroIntento());
    }
}
