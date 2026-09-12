package com.tinku.matching;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Catálogo cerrado y granular de temas (US-5 / FR-MATCH-006, T-M2-XX). Una
 * sola lectura (Tema + Trayecto en un join) arma el árbol de
 * {@code GET /api/catalogos}, con filtros opcionales nivel/curso/materia
 * (valores exactos) y valida pertenencia al catálogo vigente para el PUT de
 * temas del Tutor. El seed real entra en V20 (FASE 3); acá solo se lee.
 */
@Service
public class CatalogoService {

    private final TemaRepository temaRepository;

    public CatalogoService(TemaRepository temaRepository) {
        this.temaRepository = temaRepository;
    }

    /** Árbol completo o filtrado: cursos/materias ordenados por nombre (TreeMap),
     * temas por {@code orden}. Sin filtros -> las 3 ramas (nivel). */
    public List<CatalogoRama> arbol(String nivel, String curso, String materia) {
        Map<NivelTrayecto, Map<String, Map<String, List<Tema>>>> porRama =
                new EnumMap<>(NivelTrayecto.class);
        for (Tema tema : temaRepository.findAllConTrayecto()) {
            Trayecto tray = tema.getTrayecto();
            if (nivel != null && !nivel.equals(tray.getNivel().name())) continue;
            if (curso != null && !curso.equals(tray.getAnioOCarrera())) continue;
            if (materia != null && !materia.equals(tray.getMateria())) continue;
            porRama.computeIfAbsent(tray.getNivel(), k -> new TreeMap<>())
                    .computeIfAbsent(tray.getAnioOCarrera(), k -> new TreeMap<>())
                    .computeIfAbsent(tray.getMateria(), k -> new ArrayList<>())
                    .add(tema);
        }
        return porRama.entrySet().stream()
                .map(rama -> new CatalogoRama(rama.getKey().name(),
                        rama.getValue().entrySet().stream()
                                .map(cursoE -> new CatalogoCurso(cursoE.getKey(),
                                        cursoE.getValue().entrySet().stream()
                                                .map(materiaE -> new CatalogoMateria(materiaE.getKey(),
                                                        materiaE.getValue().stream()
                                                                .sorted(Comparator.comparingInt(Tema::getOrden))
                                                                .map(t -> new CatalogoTema(t.getId(),
                                                                        t.getNombre(), t.getDescripcion()))
                                                                .toList()))
                                                .toList()))
                                .toList()))
                .toList();
    }

    /** FR-MATCH-006: todos los ids deben existir en el catálogo vigente. */
    public boolean existen(Collection<UUID> ids) {
        return temaRepository.countByIdIn(ids) == ids.size();
    }

    public record CatalogoTema(UUID id, String nombre, String descripcion) {
    }

    public record CatalogoMateria(String nombre, List<CatalogoTema> temas) {
    }

    public record CatalogoCurso(String nombre, List<CatalogoMateria> materias) {
    }

    public record CatalogoRama(String nivel, List<CatalogoCurso> cursos) {
    }
}