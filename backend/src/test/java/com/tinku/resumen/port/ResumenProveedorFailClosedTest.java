package com.tinku.resumen.port;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-M6-05 — el puerto del proveedor LLM es fail-closed por default: mientras el
 * ADR del proveedor (T-FIN-03) esté pendiente, {@code ResumenProveedor} no hace
 * ninguna llamada de red y falla con un error claro. No se elige proveedor ni se
 * hardcodea ningún client.
 */
class ResumenProveedorFailClosedTest {

    private final ResumenProveedor proveedor = new ResumenProveedorFailClosed();

    @Test
    void sinProveedorConfiguradoFallaConErrorClaro() {
        ResumenProveedor.ResumenRequest request = new ResumenProveedor.ResumenRequest(
                UUID.randomUUID(), "texto anonimizado de prueba", null, null, null, null);

        ResumenProveedorNoConfiguradoException ex =
                assertThrows(ResumenProveedorNoConfiguradoException.class,
                        () -> proveedor.generarResumen(request));

        assertTrue(ex.getMessage().contains("ADR"));
        assertTrue(ex.getMessage().contains("T-FIN-03"));
    }
}