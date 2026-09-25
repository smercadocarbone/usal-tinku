package com.tinku.aula.web;

/** {@code grabarAudioResumen}: ADR-M3-04 — solo {@code true} para el Tutor de una clase con el adicional. */
public record TokenSesionResponse(String token, String livekitUrl, String livekitRoomId, boolean grabarAudioResumen) {
}