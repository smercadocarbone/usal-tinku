package com.tinku.identidad.repository;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    boolean existsByDni(String dni);

    /** V41: el Tutor acepta dar clases a menores. */
    boolean existsByIdAndAceptaMenoresTrue(UUID id);
    Optional<Usuario> findByDni(String dni);

    /** "Editar cuenta" (auditoría 2026-09-19): unicidad de email excluyendo al
     * propio usuario — de lo contrario, guardar el mismo email que ya tenía
     * dispararía un falso "ya registrado" (el índice único de V18 solo
     * permite un dueño por email, pero acá ESE dueño puede ser el que pide
     * el cambio). */
    boolean existsByEmailAndIdNot(String email, UUID id);

    /** FR-ID-013: perfiles de menor a cargo de un Adulto Responsable. */
    long countByAdultoResponsableIdAndTipo(UUID adultoResponsableId, TipoUsuario tipo);

    /** T-M1-13bis (auditoría 2026-09-18): listado de esos mismos perfiles —
     *  antes solo existía el alta (POST) y la baja por id (DELETE), sin forma
     *  de volver a listarlos tras cerrar la sesión del navegador. FASE2-06 /
     *  AUD-017: excluye menores en BAJA (anonimizados) — su perfil dejó de
     *  ser administrable. */
    List<Usuario> findByAdultoResponsableIdAndTipoAndEstadoCuentaNotOrderByNombre(
            UUID adultoResponsableId, TipoUsuario tipo, EstadoCuenta estadoCuentaExcluido);

    /**
     * FR-MATCH-007 / Plan_M2 paso 4: universo de candidatos para búsqueda sin
     * restricción = Tutores con matching habilitado (CAP aprobado en M1) y
     * cuenta activa. `activo_para_matching` es exactamente donde M9 apaga el
     * matching al sancionar (ver Usuario#activoParaMatching), así que leer este
     * flag ES la exclusión de suspendidos sin duplicar estado. FR-ID-028 (2026-09-25): sin
     * foto de perfil el Tutor no aparece en búsquedas (la foto es obligatoria).
     */
    @Query("""
            select u.id from Usuario u
             where u.tipo = com.tinku.identidad.model.TipoUsuario.TUTOR
               and u.activoParaMatching = true
               and u.fotoRef is not null
               and u.estadoCuenta = com.tinku.identidad.model.EstadoCuenta.ACTIVA
            """)
    List<UUID> tutoresActivosParaMatching();

    /**
     * FR-MATCH-007: de una lista ya acotada (ej. autorizaciones de un menor),
     * quedarse con los Tutores que siguen activos para matching — un Tutor puede
     * haberse suspendido (M9) DESPUÉS de haber sido autorizado.
     */
    @Query("""
            select u.id from Usuario u
             where u.id in :ids
               and u.activoParaMatching = true
               and u.fotoRef is not null
               and u.estadoCuenta = com.tinku.identidad.model.EstadoCuenta.ACTIVA
            """)
    List<UUID> idsActivosParaMatching(@Param("ids") Collection<UUID> ids);
}
