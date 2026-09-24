package com.tinku.resumen.port;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-M6-05 / T07 — el puerto del proveedor LLM es fail-closed por default: sin la
 * property {@code tinku.resumen.proveedor=gemini} (ADR-M6-03),
 * {@code ResumenProveedor} no hace ninguna llamada de red y falla con un error
 * claro que explica como activar el proveedor real.
 */
class ResumenProveedorFailClosedTest {

    private final ResumenProveedor proveedor = new ResumenProveedorFailClosed();

    @Test
    void sinPropertyGeminiFallaConErrorClaro() {
        ResumenProveedor.ResumenRequest request = new ResumenProveedor.ResumenRequest(
                UUID.randomUUID(), "texto anonimizado de prueba", null, null, null, null);

        ResumenProveedorNoConfiguradoException ex =
                assertThrows(ResumenProveedorNoConfiguradoException.class,
                        () -> proveedor.generarResumen(request));

        assertTrue(ex.getMessage().contains("gemini"));
        assertTrue(ex.getMessage().contains("ADR-M6-03"));
    }
}