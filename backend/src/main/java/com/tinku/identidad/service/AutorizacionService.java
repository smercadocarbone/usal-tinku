package com.tinku.identidad.service;

import com.tinku.identidad.model.AutorizacionTutor;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Autorizaciones de Tutor del Adulto Responsable (FR-ID-009, T-M1-11).
 *
 *  - Un AR autoriza a un Tutor para dictar a uno de SUS menores
 *    ({@link #autorizarTutor}). Solo la capacidad "Adulto Responsable" puede;
 *    un menor nunca (Artículo II).
 *  - El "no confiable" es a nivel de cuenta del AR: marca un Tutor para que
 *    deje de aparecer en los resultados de matching de esa cuenta, sin alertar
 *    a Admin ni tocar su reputación pública ({@link #marcarNoConfiable}).
 */
@Service
public class AutorizacionService {

    private final AutorizacionTutorRepository autorizacionRepo;
    private final UsuarioRepository usuarioRepo;

    public AutorizacionService(AutorizacionTutorRepository autorizacionRepo,
                               UsuarioRepository usuarioRepo) {
        this.autorizacionRepo = autorizacionRepo;
        this.usuarioRepo = usuarioRepo;
    }

    /** El usuario debe ser Adulto Responsable con esa capacidad activa (FR-ID-009, Artículo II). */
    private void exigirCapacidadAdultoResponsable(Usuario usuario) {
        if (usuario.getTipo() == TipoUsuario.MENOR
                || !usuario.isCapacidadAdultoResponsable()) {
            throw new TutorNoAutorizadoException(
                    "Solo la capacidad 'Adulto Responsable' puede autorizar a un Tutor.");
        }
    }

    /** Autoriza a un Tutor para uno de los menores a cargo del AR. Idempotente. */
    @Transactional
    public AutorizacionTutor autorizarTutor(Usuario adultoResponsable, UUID menorId, UUID tutorId) {
        exigirCapacidadAdultoResponsable(adultoResponsable);

        Usuario menor = usuarioRepo.findById(menorId)
                .orElseThrow(MenorNoPerteneceException::new);
        // FR-ID-020: solo puede operar sobre menores a su cargo.
        if (menor.getTipo() != TipoUsuario.MENOR
                || menor.getAdultoResponsable() == null
                || !menor.getAdultoResponsable().getId().equals(adultoResponsable.getId())) {
            throw new MenorNoPerteneceException();
        }

        Usuario tutor = usuarioRepo.findById(tutorId)
                .orElseThrow(TutorNoAutorizadoException::new);
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new TutorNoAutorizadoException("Solo perfiles de Tutor pueden ser autorizados.");
        }

        // Idempotente sobre la constraint única (adulto, menor, tutor).
        return autorizacionRepo
                .findByAdultoResponsableIdAndMenorIdAndTutorId(
                        adultoResponsable.getId(), menorId, tutorId)
                .orElseGet(() -> {
                    AutorizacionTutor a = new AutorizacionTutor();
                    a.setAdultoResponsable(adultoResponsable);
                    a.setMenor(menor);
                    a.setTutor(tutor);
                    a.setNoConfiable(false);
                    return autorizacionRepo.save(a);
                });
    }

    /**
     * Marca un Tutor como no confiable (o lo desmarca) para toda la cuenta del
     * AR (FR-ID-009). Solo se puede marcar un Tutor con el que la cuenta ya
     * tiene una autorización.
     */
    @Transactional
    public void marcarNoConfiable(Usuario adultoResponsable, UUID tutorId, boolean noConfiable) {
        exigirCapacidadAdultoResponsable(adultoResponsable);

        if (!autorizacionRepo.existsByAdultoResponsableIdAndTutorId(
                adultoResponsable.getId(), tutorId)) {
            throw new TutorNoAutorizadoException(
                    "Solo podés marcar a un Tutor con el que ya tenés una autorización.");
        }

        autorizacionRepo.setNoConfiableParaTutor(adultoResponsable.getId(), tutorId, noConfiable);
    }
}
