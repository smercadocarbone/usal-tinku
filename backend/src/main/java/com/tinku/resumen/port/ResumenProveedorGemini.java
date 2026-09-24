package com.tinku.resumen.port;

import com.tinku.resumen.GeminiCliente;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter de {@link ResumenProveedor} para Gemini (T07, ADR-M6-03). Activo solo
 * con la property {@code tinku.resumen.proveedor=gemini}; sin ella sigue el
 * fail-closed. Recibe SOLO {@code transcriptAnonimizado} (FR-SUM-005): el campo
 * {@code audioBase64} del {@link ResumenRequest} no se usa — con el pipeline de
 * dos llamadas (PT7) el audio va al transcriber (T08), nunca a este paso.
 */
@Component
@ConditionalOnProperty(name = "tinku.resumen.proveedor", havingValue = "gemini")
public class ResumenProveedorGemini implements ResumenProveedor {

    private final GeminiCliente geminiCliente;

    public ResumenProveedorGemini(GeminiCliente geminiCliente) {
        this.geminiCliente = geminiCliente;
    }

    @Override
    public ResumenResultado generarResumen(ResumenRequest request) {
        String texto = geminiCliente.generarContenido(armarPrompt(request.transcriptAnonimizado()));
        return new ResumenResultado(texto);
    }

    /**
     * FR-SUM-003/008: estructura fija + prohibiciones de evaluacion. Es la MISMA
     * plantilla que {@code ResumenService} persiste como {@code promptAnonimizado}
     * (auditoria); este es el prompt realmente enviado — ambas copias deben
     * evolucionar juntas.
     */
    private static String armarPrompt(String transcriptAnonimizado) {
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