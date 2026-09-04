package com.tinku.matching;

import java.util.List;
import java.util.UUID;

/**
 * Un ítem del ranking final tal como llega del servicio Python + ajustes de
 * Java (M2-D). {@code score} es el score combinado; {@code noAutorizado}
 * (FR-MATCH-005) lo setea el orquestador cuando la búsqueda fue de un menor y
 * el Tutor no está en su lista de autorización.
 */
public record ResultadoRanking(UUID tutorId, double score, boolean noAutorizado) {

    public ResultadoRanking(UUID tutorId, double score) {
        this(tutorId, score, false);
    }
}