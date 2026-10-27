package com.tinku.aula.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Cuerpo de {@code POST /api/sesiones/{id}/admitir}: a quién deja pasar el Tutor (su identity de LiveKit). */
public record AdmitirRequest(@NotNull UUID usuarioId) {
}
