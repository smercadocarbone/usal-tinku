package com.tinku.resumen.config;

import com.tinku.resumen.port.ResumenProveedor;
import com.tinku.resumen.port.ResumenProveedorFailClosed;
import com.tinku.aula.AudioResumenService;
import com.tinku.resumen.port.ResumenProveedorOpenAi;
import com.tinku.resumen.port.TranscriptSesionProveedor;
import com.tinku.resumen.port.TranscriptSesionProveedorNoDisponible;
import com.tinku.resumen.port.TranscriptSesionProveedorOpenAi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elige el {@link ResumenProveedor} por config (ADR-M6-03): solo
 * {@code tinku.resumen.llm.proveedor=gpt-4o} activa OpenAI; vacio o cualquier
 * otro valor deja el fail-closed — un typo nunca manda datos a un proveedor no
 * decidido.
 */
@Configuration
public class ResumenProveedorConfig {

    private static final Logger log = LoggerFactory.getLogger(ResumenProveedorConfig.class);

    @Bean
    ResumenProveedor resumenProveedor(
            @Value("${tinku.resumen.llm.proveedor:}") String proveedor,
            @Value("${tinku.resumen.llm.api-key:}") String apiKey,
            @Value("${tinku.resumen.llm.base-url:https://api.openai.com}") String baseUrl) {
        if ("gpt-4o".equals(proveedor)) {
            if (apiKey.isBlank()) {
                log.warn("LLM_PROVEEDOR=gpt-4o sin LLM_API_KEY: el resumen falla cerrado.");
            }
            return new ResumenProveedorOpenAi(baseUrl, apiKey);
        }
        if (!proveedor.isBlank()) {
            log.warn("LLM_PROVEEDOR='{}' no es un proveedor decidido (ADR-M6-03 solo admite "
                    + "gpt-4o): el resumen falla cerrado.", proveedor);
        }
        return new ResumenProveedorFailClosed();
    }

    /** ADR-M3-04: el transcript sale del audio de la clase con la misma cuenta de OpenAI. */
    @Bean
    TranscriptSesionProveedor transcriptSesionProveedor(
            @Value("${tinku.resumen.llm.proveedor:}") String proveedor,
            @Value("${tinku.resumen.llm.api-key:}") String apiKey,
            @Value("${tinku.resumen.llm.base-url:https://api.openai.com}") String baseUrl,
            AudioResumenService audio) {
        if ("gpt-4o".equals(proveedor)) {
            return new TranscriptSesionProveedorOpenAi(baseUrl, apiKey, audio);
        }
        return new TranscriptSesionProveedorNoDisponible();
    }
}
