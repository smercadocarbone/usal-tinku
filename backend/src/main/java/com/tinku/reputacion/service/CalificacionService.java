package com.tinku.reputacion.service;

import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.Usuario;
import com.tinku.reputacion.model.Calificacion;
import com.tinku.reputacion.repository.CalificacionRepository;
import com.tinku.reputacion.web.CalificacionOcultaResponse;
import com.tinku.reputacion.web.CalificacionResponse;
import com.tinku.reputacion.web.CalificarRequest;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Calificaciones y reputacion (Tinku, modulo M7). T-M7-02 (calificar),
 * T-M7-04 (ocultas para moderacion) y T-M7-06 (edicion dentro de 48hs).
 *
 * La direccion NUNCA viene de input: se deriva del rol del autor sobre la
 * Reserva (Plan 2.2) — Tutor → tutor_a_estudiante (oculta); beneficiario o
 * pagador → estudiante_a_tutor (publica); un tercero autenticado → 403.
 */
@Service
public class CalificacionService {

    /** FR-REP-005 — ventana de edicion de 48hs, Tabla_Tiempos fila "Calificacion". */
    public static final Duration VENTANA_EDICION = Duration.ofHours(48);

    private final CalificacionRepository calificacionRepo;
    private final SesionAprendizajeRepository sesionRepo;
    private final ReservaRepository reservaRepo;

    public CalificacionService(CalificacionRepository calificacionRepo,
                               SesionAprendizajeRepository sesionRepo,
                               ReservaRepository reservaRepo) {
        this.calificacionRepo = calificacionRepo;
        this.sesionRepo = sesionRepo;
        this.reservaRepo = reservaRepo;
    }

    @Transactional
    public CalificacionResponse calificar(Usuario autor, UUID sesionId, CalificarRequest request) {
        validarEstrellas(request.estrellas());
        SesionAprendizaje sesion = sesionRepo.findById(sesionId)
                .orElseThrow(CalificacionSesionNoEncontradaException::new);
        // FR-REP-008: solo las Sesiones que llegaron a `finalizada` se califican.
        if (!SesionAprendizaje.ESTADO_FINALIZADA.equals(sesion.getEstado())) {
            throw new SesionNoFinalizadaException();
        }
        Reserva reserva = reservaRepo.findById(sesion.getReservaId())
                .orElseThrow(CalificacionSesionNoEncontradaException::new);
        String direccion = derivarDireccion(reserva, autor);
        if (!Calificacion.DIR_ESTUDIANTE_A_TUTOR.equals(direccion)
                && request.comentario() != null && !request.comentario().isBlank()) {
            throw new ComentarioNoPermitidoException();
        }
        if (calificacionRepo.findBySesionIdAndAutorIdAndDireccion(sesionId, autor.getId(), direccion)
                .isPresent()) {
            throw new CalificacionYaExisteException();
        }

        Calificacion c = new Calificacion();
        c.setSesionId(sesionId);
        c.setAutorId(autor.getId());
        c.setDireccion(direccion);
        c.setEstrellas(request.estrellas());
        c.setComentario(Calificacion.DIR_ESTUDIANTE_A_TUTOR.equals(direccion)
                ? request.comentario() : null);
        c.setEditableHasta(Instant.now().plus(VENTANA_EDICION));
        return CalificacionResponse.from(calificacionRepo.save(c));
    }

    /**
     * Auditoría 2026-09-20: la propia calificación del autor autenticado para
     * una sesión, si la cargó — sin esto el cliente no tenía forma de saber
     * qué {@code id} usar para {@link #editar}/{@link #eliminar} al volver a
     * cargar la pantalla, ni de mostrar lo que ya calificó.
     */
    public java.util.Optional<CalificacionResponse> propia(Usuario autor, UUID sesionId) {
        return calificacionRepo.findBySesionIdAndAutorId(sesionId, autor.getId())
                .map(CalificacionResponse::from);
    }

    /** T-M7-06 / FR-REP-005: editar estrellas/comentario publicos dentro de 48hs. */
    @Transactional
    public CalificacionResponse editar(Usuario autor, UUID id, CalificarRequest request) {
        validarEstrellas(request.estrellas());
        Calificacion c = calificacionRepo.findById(id)
                .orElseThrow(CalificacionNoEncontradaException::new);
        if (!c.getAutorId().equals(autor.getId())) {
            throw new CalificacionNoPermitidaException();
        }
        if (!Calificacion.DIR_ESTUDIANTE_A_TUTOR.equals(c.getDireccion())) {
            throw new CalificacionOcultaNoEditableException();
        }
        if (Instant.now().isAfter(c.getEditableHasta())) {
            throw new CalificacionDefinitivaException();
        }
        c.setEstrellas(request.estrellas());
        c.setComentario(request.comentario());
        return CalificacionResponse.from(calificacionRepo.save(c));
    }

    /** T-M7-06 / FR-REP-005: eliminar la calificacion publica propia dentro de 48hs. */
    @Transactional
    public void eliminar(Usuario autor, UUID id) {
        Calificacion c = calificacionRepo.findById(id)
                .orElseThrow(CalificacionNoEncontradaException::new);
        if (!c.getAutorId().equals(autor.getId())) {
            throw new CalificacionNoPermitidaException();
        }
        if (!Calificacion.DIR_ESTUDIANTE_A_TUTOR.equals(c.getDireccion())) {
            throw new CalificacionOcultaNoEditableException();
        }
        if (Instant.now().isAfter(c.getEditableHasta())) {
            throw new CalificacionDefinitivaException();
        }
        calificacionRepo.delete(c);
    }

    /** T-M7-04: calificaciones ocultas (tutor_a_estudiante) del panel de Moderacion. */
    @Transactional(readOnly = true)
    public List<CalificacionOcultaResponse> calificacionesOcultas(UUID estudianteId) {
        return calificacionRepo.findOcultasPorEstudiante(estudianteId).stream()
                .map(CalificacionOcultaResponse::from).toList();
    }

    private String derivarDireccion(Reserva reserva, Usuario autor) {
        UUID id = autor.getId();
        if (reserva.getTutor().getId().equals(id)) {
            return Calificacion.DIR_TUTOR_A_ESTUDIANTE;
        }
        if (reserva.getBeneficiario().getId().equals(id) || reserva.getPagador().getId().equals(id)) {
            return Calificacion.DIR_ESTUDIANTE_A_TUTOR;
        }
        throw new CalificacionNoPermitidaException();
    }

    private void validarEstrellas(Short estrellas) {
        if (estrellas == null || estrellas < 1 || estrellas > 5) {
            throw new EstrellasInvalidasException();
        }
    }
}