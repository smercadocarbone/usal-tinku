package com.tinku.resumen;

/**
 * Plantilla del prompt del resumen (FR-SUM-003/008): estructura fija +
 * prohibiciones de evaluacion. Una sola fuente para lo que se ENVIA al proveedor
 * ({@code ResumenProveedorGemini}) y lo que se PERSISTE para auditoria
 * ({@code ResumenSesion.promptAnonimizado}) — si fueran dos copias, la auditoria
 * podria registrar un prompt distinto del que salio.
 */
public final class PromptResumen {

    private PromptResumen() {
    }

    public static String armar(String transcriptAnonimizado) {
        return "Resumi la sesion de tutoria en espanol, con tono claro y adaptado al nivel "
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
                + "\"el tutor\" y \"el estudiante\".\n"
                + "\nTranscript:\n" + transcriptAnonimizado;
    }
}
