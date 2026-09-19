package com.tinku.reservas.service;

import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.FranjaDisponibilidad;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.web.TimeSlotResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * T-M4-12: bloques de un día para el {@code DynamicTimeSlotPicker} del
 * frontend (contrato ya fijado por ese componente, F-06/{@code
 * frontend/src/components/DynamicTimeSlotPicker.tsx}, ver NOTA en
 * Tasks_Tinku_Implementacion.md). No define reglas de negocio nuevas — solo
 * orquesta franjas ya publicadas (FR-RES-012), reservas no canceladas
 * existentes (mismo criterio que la EXCLUDE de FR-RES-007) y la ventana
 * mínima de 15 min (FR-RES-013), todas ya vigentes en {@link FranjaService}/
 * {@link ReservaService}.
 */
@Service
public class HorariosDisponiblesService {

    /** FR-RES-013 — no se reserva a menos de 15 min del inicio (Tabla_Tiempos_Tinku.md). */
    private static final Duration VENTANA_MINIMA = Duration.ofMinutes(15);

    private final FranjaService franjaService;
    private final ReservaRepository reservaRepo;

    public HorariosDisponiblesService(FranjaService franjaService, ReservaRepository reservaRepo) {
        this.franjaService = franjaService;
        this.reservaRepo = reservaRepo;
    }

    public List<TimeSlotResponse> horariosDelDia(UUID tutorId, LocalDate fecha, int duracionMinutos) {
        if (duracionMinutos <= 0) {
            throw new DuracionMinutosInvalidaException("duracionMinutos debe ser positivo.");
        }
        List<FranjaDisponibilidad> franjas = franjaService.franjasQueAplicanA(tutorId, fecha);
        if (franjas.isEmpty()) {
            return List.of();
        }

        Instant desdeDia = fecha.atStartOfDay(ReservasZonaHoraria.ZONA).toInstant();
        Instant hastaDia = fecha.plusDays(1).atStartOfDay(ReservasZonaHoraria.ZONA).toInstant();
        List<Reserva> reservasDelDia = reservaRepo.findByTutor_IdAndEstadoNotAndHorarioBetween(
                tutorId, EstadoReserva.CANCELADA, desdeDia, hastaDia);

        Instant ahora = Instant.now();
        Duration duracion = Duration.ofMinutes(duracionMinutos);
        List<TimeSlotResponse> slots = new ArrayList<>();
        for (FranjaDisponibilidad f : franjas) {
            LocalTime cursor = f.getHoraInicio();
            while (!cursor.plusMinutes(duracionMinutos).isAfter(f.getHoraFin())) {
                Instant inicioBloque = fecha.atTime(cursor).atZone(ReservasZonaHoraria.ZONA).toInstant();
                Instant finBloque = inicioBloque.plus(duracion);

                boolean ocupado = reservasDelDia.stream()
                        .anyMatch(r -> seSuperponen(r, inicioBloque, finBloque, tutorId, duracion));
                boolean dentroDeVentanaMinima = ahora.plus(VENTANA_MINIMA).isAfter(inicioBloque);

                slots.add(new TimeSlotResponse(inicioBloque.toString(), inicioBloque.toString(),
                        finBloque.toString(), !ocupado && !dentroDeVentanaMinima));
                cursor = cursor.plusMinutes(duracionMinutos);
            }
        }
        return slots;
    }

    /** La duración real de una Reserva existente es la de la franja que la
     *  originó (FR-RES-023); si esa franja ya no existe (el Tutor la borró
     *  después), se aproxima con el tamaño de bloque pedido — conservador:
     *  sigue marcando el horario de inicio como ocupado, nunca lo libera. */
    private boolean seSuperponen(Reserva r, Instant inicioBloque, Instant finBloque,
                                 UUID tutorId, Duration duracionPorDefecto) {
        Instant inicioReserva = r.getHorario();
        Duration duracionReserva = franjaService.duracionFranjaQueCubre(tutorId, inicioReserva)
                .orElse(duracionPorDefecto);
        Instant finReserva = inicioReserva.plus(duracionReserva);
        return inicioBloque.isBefore(finReserva) && inicioReserva.isBefore(finBloque);
    }
}
