package com.tinku.resumen.web;

import com.tinku.resumen.model.ResumenSesion;

/**
 * {@code disponible=false} colapsa TODOS los estados no-{@code generado}
 * (pendiente, fallido, reintento agotado, suspendido por seguridad) en uno
 * solo — ver {@code ResumenService#obtenerParaParticipante} para el porqué.
 */
public record ResumenResponse(boolean disponible, String resumenFinal) {

    public static ResumenResponse from(ResumenSesion r) {
        if (r == null) {
            return new ResumenResponse(false, null);
        }
        return new ResumenResponse(true, r.getResumenFinal());
    }
}
