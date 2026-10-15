package com.tinku.shared;

/**
 * R-F F-1 (T4-H2): el admin sí tiene el rol, pero es parte del caso (denunciante, denunciado, su
 * propia credencial o CAP). Antes salía el mismo 403 que "no tenés el rol", que era falso.
 * Extiende {@link AccesoModeracionDenegadoException}: quien ya la trataba como 403 sigue igual.
 */
public class ConflictoDeInteresException extends AccesoModeracionDenegadoException {

    public static final String CODIGO = "CONFLICTO_DE_INTERES";

    public ConflictoDeInteresException() {
        super("No podés resolver un caso en el que sos parte. Lo tiene que resolver otro admin de Moderación.");
    }
}
