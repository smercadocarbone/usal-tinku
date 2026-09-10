package com.tinku.aula;

/** No existe Alerta de Seguridad para la sesión (404): la evidencia solo se
 * sube tras un kill-switch ya registrado (T-M3-08). */
public class AlertaNoEncontradaException extends RuntimeException {
    public AlertaNoEncontradaException() {
        super("No hay una alerta de seguridad (kill-switch) registrada para esta sesión.");
    }
}