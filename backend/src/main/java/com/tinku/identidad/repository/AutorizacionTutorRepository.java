package com.tinku.identidad.repository;

import com.tinku.identidad.model.AutorizacionTutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutorizacionTutorRepository extends JpaRepository<AutorizacionTutor, UUID> {

    Optional<AutorizacionTutor> findByAdultoResponsableIdAndMenorIdAndTutorId(
            UUID adultoResponsableId, UUID menorId, UUID tutorId);

    /**
     * FR-MATCH-004: la lista de Tutores autorizados a buscar al menor, excluyendo
     * los marcados {@code no_confiable} (FR-ID-009) — usada por MatchingContextoService.
     */
    List<UUID> findTutorIdsByAdultoResponsableIdAndMenorIdAndNoConfiableFalse(
            UUID adultoResponsableId, UUID menorId);

    boolean existsByAdultoResponsableIdAndTutorId(UUID adultoResponsableId, UUID tutorId);

    /**
     * FR-ID-009: el "no confiable" es a nivel de cuenta del Adulto Responsable
     * — se aplica a todas sus autorizaciones de ese Tutor, sin importar el
     * menor.
     */
    @Modifying
    @Query("""
            update AutorizacionTutor a
               set a.noConfiable = :noConfiable
             where a.adultoResponsable.id = :adultoResponsableId
               and a.tutor.id = :tutorId
            """)
    int setNoConfiableParaTutor(@Param("adultoResponsableId") UUID adultoResponsableId,
                                @Param("tutorId") UUID tutorId,
                                @Param("noConfiable") boolean noConfiable);

    /** Baja de menor (T-M1-12): se eliminan sus autorizaciones. */
    @Modifying
    void deleteByMenorId(UUID menorId);
}
