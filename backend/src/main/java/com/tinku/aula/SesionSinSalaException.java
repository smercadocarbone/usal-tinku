package com.tinku.aula;

/**
 * El participante pide el token de LiveKit antes de que el job de T-5 haya
 * creado la sala ({@code livekitRoomId} aún null). Se traduce a 422.
 */
public class SesionSinSalaException extends RuntimeException {
    public SesionSinSalaException(String message) {
        super(message);
    }
}