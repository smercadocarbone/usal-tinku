package com.tinku.reputacion.service;

import com.tinku.reputacion.repository.CalificacionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * R-E E-3 (T1-11, FR-REP-001, decisión D-4): los comentarios públicos que se ven en el perfil del
 * Tutor. Solo la calificación del Estudiante al Tutor (la del Tutor al Estudiante es oculta), solo
 * con comentario y solo si el Tutor ya muestra promedio (FR-REP-007, 5 o más). Sin ningún dato de
 * quién la escribió: el menor nunca aparece (Artículo II).
 */
@Service
public class ComentariosTutorService {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int LIMITE_MAXIMO = 20;

    private final CalificacionRepository calificacionRepo;

    public ComentariosTutorService(CalificacionRepository calificacionRepo) {
        this.calificacionRepo = calificacionRepo;
    }

    public record ComentarioPublico(int estrellas, String comentario, LocalDate fecha) {
    }

    @Transactional(readOnly = true)
    public List<ComentarioPublico> comentarios(UUID tutorId, int limite) {
        if (calificacionRepo.countPublicasPorTutor(tutorId) < ReputacionPerfilProveedor.UMBRAL_PUBLICO) {
            return List.of();
        }
        int n = Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        return calificacionRepo.comentariosPublicosPorTutor(tutorId, PageRequest.of(0, n)).stream()
                .map(c -> new ComentarioPublico(c.getEstrellas(), c.getComentario().strip(),
                        c.getCreatedAt().atZone(ZONA).toLocalDate()))
                .toList();
    }
}
