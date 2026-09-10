package com.tinku.seguridad.web;

import com.tinku.seguridad.model.DecisionAlerta;
import com.tinku.seguridad.model.TipoSancion;
import jakarta.validation.constraints.NotNull;

/** Body de POST /api/admin/moderacion/alertas-seguridad/{id}/resolver (US-2).
 * {@code tipoSancion}/{@code diasSuspension} solo aplican si {@code decision =
 * sancionar}. */
public record ResolverAlertaRequest(
        @NotNull DecisionAlerta decision,
        TipoSancion tipoSancion,
        Integer diasSuspension) {
}