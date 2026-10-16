package com.tinku.identidad.port;

import java.util.List;

/**
 * Puerto hacia el catálogo de M2 (ADR-M1-06): las materias entre las que el Tutor elige sus
 * especialidades. Implementación real: {@code com.tinku.matching.port.CatalogoMateriasProviderReal}.
 */
public interface CatalogoMateriasProvider {

    /** Materias del catálogo vigente, sin repetir, ordenadas. */
    List<String> materias();
}
