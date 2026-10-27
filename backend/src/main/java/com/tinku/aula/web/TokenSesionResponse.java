package com.tinku.aula.web;

/**
 * {@code grabarAudioResumen}: ADR-M3-04 — solo {@code true} para el Tutor de una clase con el adicional.
 * {@code pizarraHabilitada} (FR-AULA-012, ADR-M3-06): ningún participante es Menor.
 * {@code hayMenor} (ADR-M3-07): el cliente elige con esto el umbral del clasificador y si aplica el fail-closed.
 * {@code admitido}: el Tutor ya dejó pasar a este alumno; al volver a entrar se saltea la sala de espera.
 */
public record TokenSesionResponse(String token, String livekitUrl, String livekitRoomId, boolean grabarAudioResumen,
                                  boolean pizarraHabilitada, boolean hayMenor, boolean admitido) {
}
