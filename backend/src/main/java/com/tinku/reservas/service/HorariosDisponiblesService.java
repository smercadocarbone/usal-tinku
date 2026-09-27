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
import java.util.Optional;
import java.util.UUID;

/**
 * T-M4-12: bloques de un día para el {@code DynamicTimeSlotPicker} del
 * frontend (contrato ya fijado por ese componente, F-06/{@code
 * frontend/src/components/DynamicTimeSlotPicker.tsx}, ver NOTA en
 * Tasks_Tinku_Implementacion.md). No define reglas de negocio nuevas — solo
 * orquesta franjas ya publicadas (FR-RES-012), reservas no canceladas
 * existentes (mismo criterio que la EXCLUDE de FR-RES-007) y la ventana
 * mínima de 30 min (FR-RES-013), todas ya vigentes en {@link FranjaService}/
 * {@link ReservaService}.
 */
@Service
public class HorariosDisponiblesService {

    /** FR-RES-013 — la misma ventana mínima que al reservar (Tabla_Tiempos_Tinku.md). */
    private static final Duration VENTANA_MINIMA = ReservaService.VENTANA_MINIMA;

    /** D6: la agenda se parte en bloques de 30 minutos. */
    private static final int PASO_MINUTOS = 30;

    private final FranjaService franjaService;
    private final ReservaRepository reservaRepo;

    public HorariosDisponiblesService(FranjaService franjaService, ReservaRepository reservaRepo) {
        this.franjaService = franjaService;
        this.reservaRepo = reservaRepo;
    }

    /** FR-MATCH-013: horizonte del "próximo horario libre" en la búsqueda (Tabla_Tiempos_Tinku.md). */
    public static final int HORIZONTE_PROXIMO_LIBRE_DIAS = 14;

    /**
     * FR-MATCH-013: el primer bloque de 30 min libre del Tutor desde ahora + la ventana mínima,
     * dentro del horizonte; vacío si no hay. Mismas reglas que {@link #horariosDelDia} pero con
     * dos consultas por Tutor (franjas y reservas del período), no una por día: corre por cada
     * resultado de la búsqueda.
     */
    public Optional<Instant> proximoLibre(UUID tutorId) {
        List<FranjaDisponibilidad> franjas = franjaService.franjasActivas(tutorId);
        if (franjas.isEmpty()) {
            return Optional.empty();
        }
        Instant ahora = Instant.now();
        LocalDate hoy = LocalDate.now(ReservasZonaHoraria.ZONA);
        LocalDate fin = hoy.plusDays(HORIZONTE_PROXIMO_LIBRE_DIAS);
        List<Reserva> reservas = reservaRepo.findByTutor_IdAndEstadoNotAndHorarioBetween(tutorId,
                EstadoReserva.CANCELADA, hoy.atStartOfDay(ReservasZonaHoraria.ZONA).toInstant(),
                fin.plusDays(1).atStartOfDay(ReservasZonaHoraria.ZONA).toInstant());
        Duration bloque = Duration.ofMinutes(PASO_MINUTOS);
        for (LocalDate fecha = hoy; fecha.isBefore(fin); fecha = fecha.plusDays(1)) {
            LocalDate dia = fecha;
            Optional<Instant> primero = franjas.stream()
                    .filter(f -> FranjaService.aplicaA(f, dia))
                    .flatMap(f -> {
                        List<Instant> inicios = new ArrayList<>();
                        for (LocalTime c = f.getHoraInicio(); !c.plusMinutes(PASO_MINUTOS).isAfter(f.getHoraFin());
                             c = c.plusMinutes(PASO_MINUTOS)) {
                            inicios.add(dia.atTime(c).atZone(ReservasZonaHoraria.ZONA).toInstant());
                        }
                        return inicios.stream();
                    })
                    .filter(inicio -> !ahora.plus(VENTANA_MINIMA).isAfter(inicio))
                    .filter(inicio -> reservas.stream()
                            .noneMatch(r -> seSuperponen(r, inicio, inicio.plus(bloque))))
                    .min(Instant::compareTo);
            if (primero.isPresent()) {
                return primero;
            }
        }
        return Optional.empty();
    }

    public List<TimeSlotResponse> horariosDelDia(UUID tutorId, LocalDate fecha, int duracionMinutos) {
        if (!FranjaService.duracionValida(duracionMinutos)) {
            throw new DuracionMinutosInvalidaException(
                    "La duración tiene que ser de 30 a 180 minutos, en bloques de 30.");
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
                        .anyMatch(r -> seSuperponen(r, inicioBloque, finBloque));
                boolean dentroDeVentanaMinima = ahora.plus(VENTANA_MINIMA).isAfter(inicioBloque);

                slots.add(new TimeSlotResponse(inicioBloque.toString(), inicioBloque.toString(),
                        finBloque.toString(), !ocupado && !dentroDeVentanaMinima));
                // D6: los inicios van cada 30 min aunque el bloque pedido sea más largo.
                cursor = cursor.plusMinutes(PASO_MINUTOS);
            }
        }
        return slots;
    }

    /** Mismo criterio que la EXCLUDE de V30 (AUD-009): rangos semiabiertos
     *  {@code [horario, horarioFin)} — las contiguas no se superponen. */
    private boolean seSuperponen(Reserva r, Instant inicioBloque, Instant finBloque) {
        return inicioBloque.isBefore(r.getHorarioFin()) && r.getHorario().isBefore(finBloque);
    }
}
