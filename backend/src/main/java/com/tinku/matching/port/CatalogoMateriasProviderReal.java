package com.tinku.matching.port;

import com.tinku.identidad.port.CatalogoMateriasProvider;
import com.tinku.matching.repository.TrayectoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** ADR-M1-06: materias del catálogo de temas (V19/V20, {@code matching.trayectos}) para las especialidades. */
@Component
public class CatalogoMateriasProviderReal implements CatalogoMateriasProvider {

    private final TrayectoRepository trayectos;

    public CatalogoMateriasProviderReal(TrayectoRepository trayectos) {
        this.trayectos = trayectos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> materias() {
        return trayectos.materiasDistintas();
    }
}
