package com.tinku.pagos.service;

import com.tinku.identidad.port.TarifaPerfilProvider;
import com.tinku.pagos.model.TarifaTutor;
import com.tinku.pagos.repository.TarifaTutorRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Implementación de {@link TarifaPerfilProvider}: lee {@code pagos.tarifas_tutor}, sin fallback. */
@Component
public class TarifaPerfilProviderPagos implements TarifaPerfilProvider {

    private final TarifaTutorRepository tarifaRepo;

    public TarifaPerfilProviderPagos(TarifaTutorRepository tarifaRepo) {
        this.tarifaRepo = tarifaRepo;
    }

    @Override
    public Optional<BigDecimal> tarifaConfigurada(UUID tutorId) {
        return tarifaRepo.findByTutorId(tutorId).map(TarifaTutor::getPrecioSesion);
    }
}
