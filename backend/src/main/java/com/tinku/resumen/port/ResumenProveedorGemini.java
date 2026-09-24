package com.tinku.resumen.port;

import com.tinku.resumen.GeminiCliente;
import com.tinku.resumen.PromptResumen;
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
        String texto = geminiCliente.generarContenido(PromptResumen.armar(request.transcriptAnonimizado()));
        return new ResumenResultado(texto);
    }
}
