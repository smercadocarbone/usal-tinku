package com.tinku.matching;

import java.util.List;
import java.util.UUID;

/**
 * Contexto de autorización resuelto en Java ANTES de llamar al servicio Python
 * (Plan_M2, sección 3, pasos 2 y 4; FR-MATCH-004/005).
 *
 *  - {@code esMenor}: la búsqueda la hizo un menor logueado con su propia
 *    cuenta (Artículo II — el menor nunca autoriza; su Adulto Responsable es
 *    quien autoriza Tutores en su nombre).
 *  - {@code tutoresAutorizados}: ids de Tutor autorizados por el Adulto
 *    Responsable de ese menor (excluyendo {@code no_confiable}). VACÍA cuando
 *    la búsqueda es sin restricción (adulto) o cuando el menor todavía no tiene
 *    ningún Tutor autorizado (FR-MATCH-005 — en ese caso busca el universo y
 *    el endpoint marca los resultados {@code no_autorizado: true}).
 */
public record ContextoAutorizacion(boolean esMenor, List<UUID> tutoresAutorizados) {

    /** Búsqueda sin restricción de lista: adulto, o menor sin autorizados (FR-MATCH-005). */
    public static ContextoAutorizacion universo(boolean esMenor) {
        return new ContextoAutorizacion(esMenor, List.of());
    }

    /** TRUE cuando el menor ya tiene una lista de autorizados y se filtra a ella (FR-MATCH-004). */
    public boolean conRestriccion() {
        return !tutoresAutorizados.isEmpty();
    }
}