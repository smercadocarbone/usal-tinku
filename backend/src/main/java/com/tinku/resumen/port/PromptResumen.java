package com.tinku.resumen.port;

/**
 * Instrucciones fijas del resumen (FR-SUM-003/008): estructura de 5 puntos y
 * prohibiciones de evaluacion. Las usa {@code ResumenService} para persistir el
 * prompt anonimizado y el proveedor para mandarlas como mensaje de sistema — una
 * sola fuente para que lo guardado y lo enviado no diverjan.
 */
public final class PromptResumen {

    public static final String INSTRUCCIONES =
            "Resumi la sesion de tutoria en espanol, con tono claro y adaptado al nivel "
            + "escolar del estudiante. Estructura fija:\n"
            + "1. Temas tratados\n"
            + "2. Conceptos clave explicados\n"
            + "3. Ejercicios o ejemplos trabajados\n"
            + "4. Dudas que quedaron abiertas\n"
            + "5. Sugerencia de que reforzar en la proxima sesion\n"
            + "\n"
            + "Reglas: NO evalues a ninguna persona, NO uses tono moralizante y NO hagas "
            + "predicciones de desempeno. El texto esta anonimizado: no reconstruyas "
            + "identidades ni datos personales; referite a los participantes como "
            + "\"el tutor\" y \"el estudiante\".\n";

    private PromptResumen() {
    }
}
