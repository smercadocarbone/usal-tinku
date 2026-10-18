package com.tinku.matching.web;

import java.util.UUID;

/** T-TES-11b: body de POST /api/busquedas/{id}/aceptaciones. {@code tipo}: PERFIL o RESERVA. */
public record AceptacionRequest(UUID tutorId, String tipo) {
}
