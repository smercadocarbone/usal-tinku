package com.tinku.identidad.service;

import com.tinku.identidad.model.TipoArchivoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.port.ArchivoNoDisponibleException;
import com.tinku.identidad.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Bio y foto del perfil público del Tutor (U1, Spec_M1 US-7).
 *
 * <ul>
 *   <li>Solo un TUTOR edita la suya ({@link SoloTutorException} → 403).</li>
 *   <li>Bio: hasta {@value #MAX_BIO} caracteres; vacía = sin bio.</li>
 *   <li>Foto: PNG o JPEG por magic bytes (misma detección que las credenciales,
 *       AUD-007; un PDF no es una foto). La foto anterior deja de estar
 *       referenciada al reemplazarla.</li>
 *   <li>El Admin de Moderación puede borrar cualquiera de las dos (moderación).</li>
 * </ul>
 */
@Service
public class PerfilPublicoTutorService {

    public static final int MAX_BIO = 500;

    private final UsuarioRepository usuarioRepository;
    private final Almacenamiento almacenamiento;

    public PerfilPublicoTutorService(UsuarioRepository usuarioRepository, Almacenamiento almacenamiento) {
        this.usuarioRepository = usuarioRepository;
        this.almacenamiento = almacenamiento;
    }

    @Transactional
    public Usuario actualizarBio(Usuario tutor, String bio) {
        exigirTutor(tutor);
        String limpia = bio == null ? null : bio.strip();
        if (limpia != null && limpia.length() > MAX_BIO) {
            throw new IllegalArgumentException("La presentación puede tener hasta " + MAX_BIO + " caracteres.");
        }
        tutor.setBio(limpia == null || limpia.isEmpty() ? null : limpia);
        return usuarioRepository.save(tutor);
    }

    @Transactional
    public Usuario actualizarFoto(Usuario tutor, byte[] contenido) {
        exigirTutor(tutor);
        Optional<TipoArchivoCredencial> tipo = TipoArchivoCredencial.detectar(contenido);
        if (tipo.isEmpty() || tipo.get() == TipoArchivoCredencial.PDF) {
            throw new FotoPerfilInvalidaException();
        }
        String extension = tipo.get() == TipoArchivoCredencial.PNG ? "png" : "jpg";
        tutor.setFotoRef(almacenamiento.guardar(contenido, "foto-perfil." + extension));
        return usuarioRepository.save(tutor);
    }

    @Transactional
    public Usuario borrarFoto(Usuario tutor) {
        exigirTutor(tutor);
        tutor.setFotoRef(null);
        return usuarioRepository.save(tutor);
    }

    /** Bytes de la foto del Tutor, o vacío si no tiene (o el archivo ya no está). */
    @Transactional(readOnly = true)
    public Optional<byte[]> foto(UUID tutorId) {
        return usuarioRepository.findById(tutorId)
                .filter(u -> u.getTipo() == TipoUsuario.TUTOR)
                .map(Usuario::getFotoRef)
                .flatMap(ref -> {
                    try {
                        return Optional.of(almacenamiento.leer(ref));
                    } catch (ArchivoNoDisponibleException e) {
                        return Optional.empty();
                    }
                });
    }

    /** Moderación (Admin): quita la bio y/o la foto de un Tutor. */
    @Transactional
    public Usuario moderar(UUID tutorId, boolean quitarBio, boolean quitarFoto) {
        Usuario tutor = usuarioRepository.findById(tutorId)
                .filter(u -> u.getTipo() == TipoUsuario.TUTOR)
                .orElseThrow(TutorNoEncontradoException::new);
        if (quitarBio) {
            tutor.setBio(null);
        }
        if (quitarFoto) {
            tutor.setFotoRef(null);
        }
        return usuarioRepository.save(tutor);
    }

    private static void exigirTutor(Usuario usuario) {
        if (usuario.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException();
        }
    }
}
