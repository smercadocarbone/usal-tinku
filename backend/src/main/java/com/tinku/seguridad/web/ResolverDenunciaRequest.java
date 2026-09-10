package com.tinku.seguridad.web;

import com.tinku.seguridad.model.TipoSancion;
import com.tinku.shared.ResolucionDenuncia;
import jakarta.validation.constraints.NotNull;

/** Body de POST /api/admin/moderacion/denuncias/{id}/resolver (US-4/5). La
 * sanción solo corresponde a resolución fundada; {@code escalada} fuerza
 * suspensión definitiva sin pedir {@code tipoSancion} (FR-SEC-009). */
public record ResolverDenunciaRequest(
        @NotNull ResolucionDenuncia resolucion,
        TipoSancion tipoSancion,
        Integer diasSuspension) {
}