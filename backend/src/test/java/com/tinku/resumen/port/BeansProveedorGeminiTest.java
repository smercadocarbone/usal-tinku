package com.tinku.resumen.port;

import com.tinku.resumen.GeminiCliente;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T07 — los beans reales de Gemini se activan solo con
 * {@code tinku.resumen.proveedor=gemini}; sin la property siguen los fail-closed
 * (tests y dev no llaman a Google). Régimen: si la condicion falla, aqui hay
 * 2 beans del mismo puerto → {@code NoUniqueBeanDefinitionException}.
 */
class BeansProveedorGeminiTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean("geminiCliente", GeminiCliente.class, () -> new GeminiCliente("clave-de-prueba"))
            .withUserConfiguration(ResumenProveedorGemini.class, TranscriptSesionProveedorGemini.class,
                    ResumenProveedorFailClosed.class, TranscriptSesionProveedorNoDisponible.class);

    @Test
    void sinPropertyGemini_siguenLosFailClosed() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ResumenProveedor.class))
                    .isInstanceOf(ResumenProveedorFailClosed.class);
            assertThat(ctx.getBean(TranscriptSesionProveedor.class))
                    .isInstanceOf(TranscriptSesionProveedorNoDisponible.class);
        });
    }

    @Test
    void conPropertyGemini_seActivanLosAdaptersDeGemini() {
        contexto.withPropertyValues("tinku.resumen.proveedor=gemini").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ResumenProveedor.class))
                    .isInstanceOf(ResumenProveedorGemini.class);
            assertThat(ctx.getBean(TranscriptSesionProveedor.class))
                    .isInstanceOf(TranscriptSesionProveedorGemini.class);
        });
    }
}