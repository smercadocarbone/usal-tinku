package com.tinku.matching;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * T-M2-05 / T-M2-06: resolución del contexto de autorización y exclusión de
 * Tutores suspendidos, SIEMPRE antes del cálculo semántico (Plan_M2, sección 3).
 *
 *  - Frases exactas de negocio que esto encierra (referenciar esta clase, no
 *    repetir): un menor logueado con su propia cuenta solo ve los Tutores de la
 *    lista de Autorización de su Adulto Responsable (FR-MATCH-004); si todavía
 *    no tiene ninguno autorizado, busca el universo y el endpoint marca cada
 *    resultado {@code no_autorizado: true} (FR-MATCH-005).
 *  - La exclusión de suspendidos (FR-MATCH-007) no requiere interrogar a M9
 *    con un stub: M1 mantiene {@code usuarios.activo_para_matching} y M9 lo
 *    apaga al sancionar (ver {@link Usuario#activoParaMatching}). Leer ese
 *    flag ES la consulta a M9 declarada en el Plan — sin duplicar el estado.
 */
@Service
public class MatchingContextoService {

    private final UsuarioRepository usuarioRepo;
    private final AutorizacionTutorRepository autorizacionRepo;

    public MatchingContextoService(UsuarioRepository usuarioRepo,
                                   AutorizacionTutorRepository autorizacionRepo) {
        this.usuarioRepo = usuarioRepo;
        this.autorizacionRepo = autorizacionRepo;
    }

    /**
     * Paso 2 del Plan: el contexto de autorización a partir de la cuenta que busca.
     * Menor -> lista de su Adulto Responsable (sin {@code no_confiable}). Cualquier
     * otro perfil (adulto Estudiante / Adulto Responsable) -> universo, sin restricción.
     */
    @Transactional
    public ContextoAutorizacion resolverContexto(Usuario buscador) {
        if (buscador.getTipo() == TipoUsuario.MENOR) {
            Usuario adulto = buscador.getAdultoResponsable();
            if (adulto == null) {
                throw new IllegalStateException(
                        "Esquema roto: un menor siempre tiene adultoResponsable (FR-ID-020).");
            }
            List<UUID> autorizados = autorizacionRepo
                    .findTutorIdsByAdultoResponsableIdAndMenorIdAndNoConfiableFalse(
                            adulto.getId(), buscador.getId());
            return new ContextoAutorizacion(true, autorizados);
        }
        return ContextoAutorizacion.universo(false);
    }

    /**
     * Pasos 3 y 4 del Plan: el conjunto acotado de candidatos que se envía al
     * servicio Python. Es la lista de autorización intersectada con los activos
     * (un Tutor puede haberse suspendido DESPUÉS de ser autorizado), o el
     * universo de tutores activos cuando no hay restricción (incluye el caso
     * FR-MATCH-005 de menor sin autorizados).
     */
    @Transactional
    public List<UUID> tutoresCandidatos(Usuario buscador) {
        ContextoAutorizacion contexto = resolverContexto(buscador);
        if (contexto.esMenor() && contexto.conRestriccion()) {
            return usuarioRepo.idsActivosParaMatching(contexto.tutoresAutorizados());
        }
        return usuarioRepo.tutoresActivosParaMatching();
    }

    /** Saber si la búsqueda la hace un menor (el orquestador de M2-D lo usa para marcar). */
    public boolean esBusquedaDeMenor(Usuario buscador) {
        return buscador.getTipo() == TipoUsuario.MENOR;
    }
}