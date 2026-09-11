package com.tinku.pagos.service;

import com.tinku.pagos.repository.TarifaTutorRepository;
import com.tinku.reservas.port.TarifaProveedor;
import com.tinku.reservas.service.TarifaNoConfiguradaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Implementación real del puerto {@code TarifaProveedor} (Spec M5 US-6,
 * FR-PAG-006, Chunk M5-H): el precio por sesión vigente del Tutor viven en
 * {@code pagos.tarifas_tutor}, que el Tutor actualiza desde su perfil.
 *
 * Fallback de desarrollo/pruebas: si el Tutor todavía no configuró su tarifa
 * (no hay fila), se devuelve el valor de {@code tinku.reservas.tarifa-stub}
 * cuando está definido — permite que el resto de los módulos (M4/M5) prueben
 * sin sembrar tarifas. Si tampoco hay stub, falla al momento de usarse con
 * mensaje claro (no se inventa un precio en silencio), misma filosofía que
 * LiveKitService sin credenciales.
 */
@Component
public class TarifaProveedorTutor implements TarifaProveedor {

    private final TarifaTutorRepository tarifaRepo;
    private final BigDecimal tarifaStub;

    public TarifaProveedorTutor(TarifaTutorRepository tarifaRepo,
                                @Value("${tinku.reservas.tarifa-stub:}") String tarifaStub) {
        this.tarifaRepo = tarifaRepo;
        this.tarifaStub = tarifaStub == null || tarifaStub.isBlank()
                ? null
                : new BigDecimal(tarifaStub.trim());
    }

    @Override
    public BigDecimal tarifaPorSesion(UUID tutorId) {
        return tarifaRepo.findByTutorId(tutorId)
                .map(t -> t.getPrecioSesion())
                .orElseGet(() -> {
                    if (tarifaStub == null) {
                        throw new TarifaNoConfiguradaException(
                                "El Tutor todavía no configuró su tarifa por sesión (Spec M5 US-6 / FR-PAG-006). "
                                        + "Para desarrollo, definí tinku.reservas.tarifa-stub (o RESERVAS_TARIFA_STUB).");
                    }
                    return tarifaStub;
                });
    }
}