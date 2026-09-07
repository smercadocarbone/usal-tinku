package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.FranjaDisponibilidad;
import com.tinku.reservas.repository.FranjaDisponibilidadRepository;
import com.tinku.reservas.web.FranjaResponse;
import com.tinku.reservas.web.PublicarFranjaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Publicación de disponibilidad del Tutor (US-1, FR-RES-012; T-M4-02).
 * La validación de "el horario cae dentro de una franja publicada y activa"
 * vive acá y la reutilizan SolicitudService (crear) y ReservaService (aprobar).
 */
@Service
public class FranjaService {

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
        LocalDateTime punto = horario.atZone(ReservasZonaHoraria.ZONA).toLocalDateTime();
        int diaSemana = punto.getDayOfWeek().getValue(); // ISO: 1=lu..7=do
        return franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId).stream()
                .anyMatch(f -> cubre(f, diaSemana, punto));
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