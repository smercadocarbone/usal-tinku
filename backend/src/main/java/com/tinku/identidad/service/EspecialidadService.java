package com.tinku.identidad.service;

import com.tinku.identidad.dto.EspecialidadResponse;
import com.tinku.identidad.dto.GuardarEspecialidadesRequest;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EspecialidadTutor;
import com.tinku.identidad.model.EstadoEspecialidad;
import com.tinku.identidad.model.MetodoVerificacion;
import com.tinku.identidad.model.NivelEspecialidad;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.CatalogoMateriasProvider;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.EspecialidadTutorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Especialidades del Tutor (ADR-M1-06, decisión 1): de 1 a 3, materia del catálogo + nivel
 * máximo. El estado de cada una sale de las credenciales aprobadas que la cubren. Solo se usa con
 * {@code tinku.especialidades.habilitado} encendido (lo controla quien llama).
 */
@Service
public class EspecialidadService {

    public static final int MAX_ESPECIALIDADES = 3;

    private final EspecialidadTutorRepository especialidadRepo;
    private final CredencialAcademicaRepository credencialRepo;
    private final UsuarioRepository usuarioRepo;
    private final CatalogoMateriasProvider catalogo;

    public EspecialidadService(EspecialidadTutorRepository especialidadRepo,
                               CredencialAcademicaRepository credencialRepo,
                               UsuarioRepository usuarioRepo,
                               CatalogoMateriasProvider catalogo) {
        this.especialidadRepo = especialidadRepo;
        this.credencialRepo = credencialRepo;
        this.usuarioRepo = usuarioRepo;
        this.catalogo = catalogo;
    }

    public List<String> materias() {
        return catalogo.materias();
    }

    /** Especialidades con su estado, en el orden en que se declararon. */
    @Transactional(readOnly = true)
    public List<EspecialidadResponse> listar(UUID tutorId) {
        Map<UUID, EstadoEspecialidad> estados = new HashMap<>();
        for (Object[] fila : credencialRepo.coberturasAprobadas(tutorId)) {
            EstadoEspecialidad estado = fila[1] == MetodoVerificacion.REGISTRO_OFICIAL
                    ? EstadoEspecialidad.VERIFICADA : EstadoEspecialidad.RESPALDO_DOCUMENTAL;
            estados.merge((UUID) fila[0], estado, (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        return especialidadRepo.findByTutorIdOrderByCreatedAtAsc(tutorId).stream()
                .map(e -> new EspecialidadResponse(e.getId(), e.getMateria(), e.getNivelMaximo(),
                        estados.getOrDefault(e.getId(), EstadoEspecialidad.DECLARADA)))
                .toList();
    }

    public boolean tieneEspecialidades(UUID tutorId) {
        return !especialidadRepo.findByTutorIdOrderByCreatedAtAsc(tutorId).isEmpty();
    }

    /**
     * Reemplaza la lista completa. Una especialidad con la misma materia y el mismo nivel se
     * conserva (y con ella, su verificación); cualquier cambio la vuelve a "declarada", porque la
     * credencial que la cubría la cubría en ese nivel.
     */
    @Transactional
    public List<EspecialidadResponse> guardar(Usuario tutor, GuardarEspecialidadesRequest request) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException();
        }
        List<GuardarEspecialidadesRequest.Especialidad> pedidas =
                request == null || request.especialidades() == null ? List.of() : request.especialidades();
        if (pedidas.isEmpty() || pedidas.size() > MAX_ESPECIALIDADES) {
            throw new EspecialidadInvalidaException("Elegí entre 1 y " + MAX_ESPECIALIDADES + " especialidades.");
        }
        Map<String, String> delCatalogo = new HashMap<>();
        catalogo.materias().forEach(m -> delCatalogo.put(m.toLowerCase(Locale.ROOT), m));
        Set<String> vistas = new HashSet<>();
        List<EspecialidadTutor> nuevas = new ArrayList<>();
        for (GuardarEspecialidadesRequest.Especialidad p : pedidas) {
            if (p == null || p.materia() == null || p.materia().isBlank() || p.nivelMaximo() == null) {
                throw new EspecialidadInvalidaException("Cada especialidad necesita una materia y un nivel.");
            }
            String materia = delCatalogo.get(p.materia().strip().toLowerCase(Locale.ROOT));
            if (materia == null) {
                throw new EspecialidadInvalidaException("Elegí la materia de la lista.");
            }
            if (!vistas.add(materia)) {
                throw new EspecialidadInvalidaException("No repitas la misma materia.");
            }
            EspecialidadTutor e = new EspecialidadTutor();
            e.setTutorId(tutor.getId());
            e.setMateria(materia);
            e.setNivelMaximo(p.nivelMaximo());
            nuevas.add(e);
        }

        usuarioRepo.bloquearPorId(tutor.getId()); // serializa con la revisión del Admin
        List<EspecialidadTutor> actuales = especialidadRepo.findByTutorIdOrderByCreatedAtAsc(tutor.getId());
        List<EspecialidadTutor> aBorrar = actuales.stream()
                .filter(a -> nuevas.stream().noneMatch(n -> mismas(a, n))).toList();
        especialidadRepo.deleteAll(aBorrar);
        especialidadRepo.flush(); // la misma materia con otro nivel choca con la única (tutor, materia)
        nuevas.stream().filter(n -> actuales.stream().noneMatch(a -> mismas(a, n)))
                .forEach(especialidadRepo::save);
        especialidadRepo.flush();
        return listar(tutor.getId());
    }

    private static boolean mismas(EspecialidadTutor a, EspecialidadTutor b) {
        return a.getMateria().equals(b.getMateria()) && a.getNivelMaximo() == b.getNivelMaximo();
    }

    /**
     * Al aprobar: cada especialidad cubierta tiene que ser del Tutor de la credencial y no estar
     * por encima del nivel que respalda el tipo (SPIKE §4.3). La materia afín la decide el Admin.
     */
    public Set<UUID> validarCobertura(CredencialAcademica credencial, Collection<UUID> ids) {
        Set<UUID> cubiertas = ids == null ? Set.of() : new HashSet<>(ids);
        if (cubiertas.isEmpty()) {
            return cubiertas;
        }
        Optional<NivelEspecialidad> tope = credencial.getTipoDocumento().nivelMaximoQueRespalda();
        if (tope.isEmpty()) {
            throw new RevisionCredencialInvalidaException("Este tipo de credencial no respalda especialidades: "
                    + "aprobala sin marcar ninguna.");
        }
        UUID tutorId = credencial.getTutor().getId();
        for (UUID id : cubiertas) {
            EspecialidadTutor e = especialidadRepo.findById(id)
                    .filter(x -> x.getTutorId().equals(tutorId))
                    .orElseThrow(() -> new RevisionCredencialInvalidaException(
                            "Esa especialidad no es de este Tutor."));
            if (!e.getNivelMaximo().hasta(tope.get())) {
                throw new RevisionCredencialInvalidaException("Esta credencial respalda hasta el nivel "
                        + nombre(tope.get()) + ": no cubre " + e.getMateria() + " en el nivel "
                        + nombre(e.getNivelMaximo()) + ".");
            }
        }
        return cubiertas;
    }

    private static String nombre(NivelEspecialidad n) {
        return n.name().toLowerCase(Locale.ROOT);
    }
}
