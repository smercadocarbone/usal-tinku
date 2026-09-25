package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.FranjaDisponibilidad;
import com.tinku.reservas.repository.FranjaDisponibilidadRepository;
import com.tinku.reservas.web.FranjaResponse;
import com.tinku.reservas.web.PublicarFranjaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Publicación de disponibilidad del Tutor (US-1, FR-RES-012; T-M4-02).
 * La validación de "el horario cae dentro de una franja publicada y activa"
 * vive acá y la reutilizan SolicitudService (crear) y ReservaService (aprobar).
 */
@Service
public class FranjaService {

    /** FR-RES-024 — franjas de 30 a 180 minutos (Tabla_Tiempos_Tinku.md). */
    private static final Duration DURACION_MINIMA = Duration.ofMinutes(30);
    private static final Duration DURACION_MAXIMA = Duration.ofMinutes(180);

    private final FranjaDisponibilidadRepository franjaRepo;

    public FranjaService(FranjaDisponibilidadRepository franjaRepo) {
        this.franjaRepo = franjaRepo;
    }

    /** Solo perfiles TUTOR publican franjas (FR-RES-012). */
    @Transactional
    public FranjaDisponibilidad publicar(Usuario tutor, PublicarFranjaRequest request) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException("Solo las cuentas de Tutor pueden publicar disponibilidad.");
        }
        if (!request.horaFin().isAfter(request.horaInicio())) {
            throw new HorarioFueraDeFranjaException("horaFin debe ser posterior a horaInicio.");
        }
        long minutos = Duration.between(request.horaInicio(), request.horaFin()).toMinutes();
        if (minutos < DURACION_MINIMA.toMinutes() || minutos > DURACION_MAXIMA.toMinutes()) {
            throw new DuracionFranjaInvalidaException(
                    "La duración de la franja debe ser de 30 a 180 minutos (FR-RES-024).");
        }

        if (seSuperpone(tutor.getId(), request)) {
            throw new FranjaSuperpuestaException();
        }

        FranjaDisponibilidad franja = new FranjaDisponibilidad();
        franja.setTutor(tutor);
        franja.setDiaSemana(request.diaSemana());
        franja.setFechaEspecifica(request.fechaEspecifica());
        franja.setHoraInicio(request.horaInicio());
        franja.setHoraFin(request.horaFin());
        franja.setActiva(true);
        return franjaRepo.save(franja);
    }

    /**
     * AUD-025: dos franjas activas del mismo Tutor que cubren el mismo día y se pisan en
     * horario. Antes {@link #franjaQueCubre} elegía una con {@code findFirst()} en orden
     * arbitrario. Cruza los dos modos: una semanal choca con una puntual de ese día de la
     * semana. La base cubre además los choques dentro de un mismo modo (V34).
     */
    private boolean seSuperpone(UUID tutorId, PublicarFranjaRequest nueva) {
        Short diaNueva = nueva.diaSemana() != null
                ? nueva.diaSemana()
                : toDomingoCero(nueva.fechaEspecifica().getDayOfWeek().getValue());
        return franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId).stream()
                .filter(f -> mismoDia(f, nueva, diaNueva))
                .anyMatch(f -> nueva.horaInicio().isBefore(f.getHoraFin())
                        && f.getHoraInicio().isBefore(nueva.horaFin()));
    }

    private boolean mismoDia(FranjaDisponibilidad f, PublicarFranjaRequest nueva, short diaNueva) {
        if (nueva.fechaEspecifica() != null && f.getFechaEspecifica() != null) {
            return nueva.fechaEspecifica().equals(f.getFechaEspecifica());
        }
        short diaExistente = f.getDiaSemana() != null
                ? f.getDiaSemana()
                : toDomingoCero(f.getFechaEspecifica().getDayOfWeek().getValue());
        return diaExistente == diaNueva;
    }

    /** FR-RES-012: true si dentro de una franja activa del tutor que cubre ese día/hora. */
    public boolean estaDentroDeFranjaActiva(UUID tutorId, Instant horario) {
        return franjaQueCubre(tutorId, horario).isPresent();
    }

    /** D6 regla 1: una reserva dura de 30 a 180 minutos, en bloques de 30. */
    public static boolean duracionValida(Integer duracionMinutos) {
        return duracionMinutos != null
                && duracionMinutos >= DURACION_MINIMA.toMinutes()
                && duracionMinutos <= DURACION_MAXIMA.toMinutes()
                && duracionMinutos % 30 == 0;
    }

    /**
     * D6 reglas 2 y 3: franja activa que contiene ENTERO {@code [inicio, inicio+duracion)}
     * con {@code inicio} alineado a un bloque de 30 minutos desde el comienzo de la franja.
     */
    public Optional<FranjaDisponibilidad> franjaQueContiene(UUID tutorId, Instant inicio, int duracionMinutos) {
        if (!duracionValida(duracionMinutos)) {
            return Optional.empty();
        }
        return franjaQueCubre(tutorId, inicio).filter(f -> {
            LocalTime ini = inicio.atZone(ReservasZonaHoraria.ZONA).toLocalTime();
            long desdeInicio = Duration.between(f.getHoraInicio(), ini).toMinutes();
            LocalTime fin = ini.plusMinutes(duracionMinutos);
            boolean alineado = desdeInicio % 30 == 0;
            // fin.isAfter(ini): la reserva no cruza la medianoche.
            boolean cabe = !fin.isAfter(f.getHoraFin()) && fin.isAfter(ini);
            return alineado && cabe;
        });
    }

    /** Franjas activas publicadas por el Tutor (GET /api/tutores/{id}/franjas). */
    public List<FranjaDisponibilidad> franjasActivas(UUID tutorId) {
        return franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId);
    }

    /**
     * Devuelve la franja activa que cubre {@code horario}, si existe. Base de
     * {@link #franjaQueContiene} (desde AUD-020 la duración sale de la Reserva,
     * no de la franja).
     */
    public Optional<FranjaDisponibilidad> franjaQueCubre(UUID tutorId, Instant horario) {
        LocalDateTime punto = horario.atZone(ReservasZonaHoraria.ZONA).toLocalDateTime();
        int diaSemana = punto.getDayOfWeek().getValue(); // ISO: 1=lu..7=do
        return franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId).stream()
                .filter(f -> cubre(f, diaSemana, punto))
                .findFirst();
    }

    /** T-M4-12: franjas activas del Tutor que aplican a una fecha del calendario
     *  (semanal por {@code diaSemana}, o puntual por {@code fechaEspecifica}). */
    public List<FranjaDisponibilidad> franjasQueAplicanA(UUID tutorId, LocalDate fecha) {
        short diaDomingoCero = toDomingoCero(fecha.getDayOfWeek().getValue());
        return franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId).stream()
                .filter(f -> f.getFechaEspecifica() != null
                        ? f.getFechaEspecifica().equals(fecha)
                        : f.getDiaSemana() != null && f.getDiaSemana() == diaDomingoCero)
                .toList();
    }

    private boolean cubre(FranjaDisponibilidad f, int diaSemana, LocalDateTime punto) {
        if (f.getFechaEspecifica() != null) {
            if (!f.getFechaEspecifica().equals(punto.toLocalDate())) {
                return false;
            }
        } else if (f.getDiaSemana() == null
                || f.getDiaSemana() != toDomingoCero(diaSemana)) {
            return false;
        }
        return !punto.toLocalTime().isBefore(f.getHoraInicio())
                && punto.toLocalTime().isBefore(f.getHoraFin());
    }

    /** Convierte día ISO (1=lu..7=do) a 0=domingo..6=sábado (formato de franjas). */
    private short toDomingoCero(int diaIso) {
        return (short) (diaIso % 7);
    }
}