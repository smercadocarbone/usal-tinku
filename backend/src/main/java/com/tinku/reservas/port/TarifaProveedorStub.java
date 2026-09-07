package com.tinku.reservas.port;

import com.tinku.reservas.service.TarifaNoConfiguradaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stub del puerto {@link TarifaProveedor} para el Chunk M4-B: M5 (tarifa del
 * Tutor, FR-PAG-006) todavía no existe como módulo. Devuelve el valor de
 * {@code tinku.reservas.tarifa-stub} (para desarrollo/pruebas) y, si está vacío,
 * falla al momento de usarse con mensaje claro — mismo enfoque que LiveKitService
 * sin credenciales (el backend arranca igual, no se inventa un precio en silencio).
 */
@Primary
@Component
public class TarifaProveedorStub implements TarifaProveedor {

    private final BigDecimal tarifaStub;

    public TarifaProveedorStub(@Value("${tinku.reservas.tarifa-stub:}") String tarifaStub) {
        this.tarifaStub = tarifaStub == null || tarifaStub.isBlank()
                ? null
                : new BigDecimal(tarifaStub.trim());
    }

    @Override
    public BigDecimal tarifaPorSesion(UUID tutorId) {
        if (tarifaStub == null) {
            throw new TarifaNoConfiguradaException(
                    "M5 (tarifa del Tutor, Spec M5 US-6 / FR-PAG-006) todavía no está implementado. "
                            + "Para desarrollo, definí tinku.reservas.tarifa-stub (o RESERVAS_TARIFA_STUB).");
        }
        return tarifaStub;
    }
}