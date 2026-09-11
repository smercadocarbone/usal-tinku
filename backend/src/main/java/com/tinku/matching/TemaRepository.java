package com.tinku.matching;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TemaRepository extends JpaRepository<Tema, UUID> {

    /** Toda la lista de temas con su trayecto en una sola lectura (sin N+1).
     * El orden se resuelve en {@link CatalogoService} (cursos/materias por
     * nombre, temas por {@code orden}). */
    @Query("select t from Tema t join fetch t.trayecto")
    List<Tema> findAllConTrayecto();

    /** FR-MATCH-006: cuántos de los ids dados existen en el catálogo vigente.
     * Si en la BD hay menos que los pedidos, alguno es desconocido. */
    long countByIdIn(Collection<UUID> ids);
}