package com.tinku.aula.web;

/** {@code grabarAudioResumen}: ADR-M3-04 — solo {@code true} para el Tutor de una clase con el adicional. */
/** {@code pizarraHabilitada} (FR-AULA-012, ADR-M3-06): ningún participante es Menor. */
public record TokenSesionResponse(String token, String livekitUrl, String livekitRoomId, boolean grabarAudioResumen,
                                  boolean pizarraHabilitada) {
}