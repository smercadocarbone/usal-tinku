package com.tinku.aula;

/**
 * No hay una confirmación de la rama "adultos" pendiente para esta sesión
 * (422): el kill-switch no se disparó, la sesión es de rama menor (que corta
 * directo, sin confirmación), la confirmación ya se respondió, o el que
 * responde es el propio detectado (US-7: responde el OTRO participante).
 */
public class ConfirmacionNoPendienteException extends RuntimeException {
    public ConfirmacionNoPendienteException() {
        super("No hay una confirmación de kill-switch pendiente en esta sesión, "
                + "o el detectado no puede confirmarse a sí mismo.");
    }
}