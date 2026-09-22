package com.tinku.aula;

/**
 * Se pide el token de LiveKit para una sesión ya terminada o cortada (AUD-001):
 * no se emiten tokens nuevos para una sala que el corte cerró. Se traduce a 422.
 */
public class SesionCerradaException extends RuntimeException {
    public SesionCerradaException() {
        super("La sesión ya terminó o fue cortada: no admite nuevas conexiones.");
    }
}
