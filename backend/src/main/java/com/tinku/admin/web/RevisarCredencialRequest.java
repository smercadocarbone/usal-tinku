package com.tinku.admin.web;

import com.tinku.identidad.model.MetodoVerificacion;
import com.tinku.identidad.model.MotivoRechazoCredencial;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Body de POST /api/admin/moderacion/credenciales/{id}/resolver (US-1, FR-ADM-001).
 *
 * <p>ADR-M1-06 (solo con {@code tinku.especialidades.habilitado} encendido; apagado se ignoran):
 * al aprobar, {@code metodoVerificacion} (obligatorio) y {@code especialidadesCubiertas}; al
 * rechazar, {@code motivoRechazo} (obligatorio) y {@code detalleRechazo} (obligatorio si el motivo
 * es OTRO).</p>
 */
public record RevisarCredencialRequest(
        @NotNull DecisionCredencial decision,
        MetodoVerificacion metodoVerificacion,
        List<UUID> especialidadesCubiertas,
        MotivoRechazoCredencial motivoRechazo,
        String detalleRechazo) {
}
