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
import java.time.LocalDateTime;
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

        FranjaDisponibilidad franja = new FranjaDisponibilidad();
        franja.setTutor(tutor);
        franja.setDiaSemana(request.diaSemana());
        franja.setFechaEspecifica(request.fechaEspecifica());
        franja.setHoraInicio(request.horaInicio());
        franja.setHoraFin(request.horaFin());
        franja.setActiva(true);
        return franjaRepo.save(franja);
    }

    /** FR-RES-012: true si dentro de una franja activa del tutor que cubre ese día/hora. */
    public boolean estaDentroDeFranjaActiva(UUID tutorId, Instant horario) {
        return franjaQueCubre(tutorId, horario).isPresent();
    }

    /**
     * Devuelve la franja activa que cubre {@code horario}, si existe. La
     * reutilizan ReservaService/SolicitudService (decisión booleana) y M3
     * (T-M3-03/05: la duración de la franja define el fin agendado).
     */
    public Optional<FranjaDisponibilidad> franjaQueCubre(UUID tutorId, Instant horario) {
        LocalDateTime punto = horario.atZone(ReservasZonaHoraria.ZONA).toLocalDateTime();
        int diaSemana = punto.getDayOfWeek().getValue(); // ISO: 1=lu..7=do
        return franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId).stream()
                .filter(f -> cubre(f, diaSemana, punto))
                .findFirst();
    }

    /**
     * Duración planificada de la franja que cubre {@code horario} — el job de
     * corte automático (T-M3-05) se programa en {@code horario + duración + 5min}.
     */
    public Optional<Duration> duracionFranjaQueCubre(UUID tutorId, Instant horario) {
        return franjaQueCubre(tutorId, horario)
                .map(f -> Duration.between(f.getHoraInicio(), f.getHoraFin()));
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