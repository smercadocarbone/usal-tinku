package com.tinku.matching.port;

import com.tinku.aula.port.ContenidoAnatomicoProveedor;
import com.tinku.matching.repository.PerfilTutorTemasRepository;
import com.tinku.reservas.port.MateriasTutorProveedor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Materias de la clase desde el catálogo de M2 (ADR-M3-07 §4bis). */
@Component
public class MateriasClaseReal implements MateriasTutorProveedor, ContenidoAnatomicoProveedor {

    private final PerfilTutorTemasRepository temasRepo;

    public MateriasClaseReal(PerfilTutorTemasRepository temasRepo) {
        this.temasRepo = temasRepo;
    }

    @Override
    public boolean dicta(UUID tutorId, UUID trayectoId) {
        return temasRepo.materiasDelTutor(tutorId).stream().anyMatch(m -> m.trayectoId().equals(trayectoId));
    }

    @Override
    public boolean esContenidoAnatomico(UUID trayectoId) {
        return temasRepo.esContenidoAnatomico(trayectoId);
    }
}
