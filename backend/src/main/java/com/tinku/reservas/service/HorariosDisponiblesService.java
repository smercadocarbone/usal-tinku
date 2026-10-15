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
import java.time.ZonedDateTime;
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
 * mínima de 21 min (FR-RES-013), todas ya vigentes en {@link FranjaService}/
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
        List<Reserva> reservas = reservasEntre(tutorId, hoy, fin);
        Duration bloque = Duration.ofMinutes(PASO_MINUTOS);
        for (LocalDate fecha = hoy; fecha.isBefore(fin); fecha = fecha.plusDays(1)) {
            for (Instant inicio : inicios(franjasDelDia(franjas, fecha), fecha, PASO_MINUTOS)) {
                if (!ahora.plus(VENTANA_MINIMA).isAfter(inicio)
                        && reservas.stream().noneMatch(r -> seSuperponen(r, inicio, inicio.plus(bloque)))) {
                    return Optional.of(inicio);
                }
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
        List<Reserva> reservasDelDia = reservasEntre(tutorId, fecha, fecha.plusDays(1));

        Instant ahora = Instant.now();
        Duration duracion = Duration.ofMinutes(duracionMinutos);
        List<TimeSlotResponse> slots = new ArrayList<>();
        for (Instant inicioBloque : inicios(franjas, fecha, duracionMinutos)) {
            Instant finBloque = inicioBloque.plus(duracion);
            boolean ocupado = reservasDelDia.stream()
                    .anyMatch(r -> seSuperponen(r, inicioBloque, finBloque));
            boolean dentroDeVentanaMinima = ahora.plus(VENTANA_MINIMA).isAfter(inicioBloque);
            slots.add(new TimeSlotResponse(inicioBloque.toString(), inicioBloque.toString(),
                    finBloque.toString(), !ocupado && !dentroDeVentanaMinima));
        }
        return slots;
    }

    /**
     * R-D D-2: inicios posibles de un bloque de {@code duracionMinutos} sobre los rangos unidos del
     * día, cada 30 min desde el comienzo de cada rango (D6), ordenados y sin duplicados. En minutos
     * enteros: no da la vuelta a la medianoche.
     */
    private static List<Instant> inicios(List<FranjaDisponibilidad> franjasDelDia, LocalDate fecha,
                                         int duracionMinutos) {
        ZonedDateTime medianoche = fecha.atStartOfDay(ReservasZonaHoraria.ZONA);
        List<Instant> inicios = new ArrayList<>();
        for (FranjaService.RangoMinutos r : FranjaService.unir(franjasDelDia)) {
            for (int m = r.desde(); m + duracionMinutos <= r.hasta(); m += PASO_MINUTOS) {
                inicios.add(medianoche.plusMinutes(m).toInstant());
            }
        }
        return inicios;
    }

    /** Rango libre de un día, en hora local ("HH:mm"). */
    public record RangoLibre(String desde, String hasta) {
    }

    /** Días con al menos un rango libre. */
    public record DiaDisponible(LocalDate fecha, List<RangoLibre> rangos) {
    }

    /**
     * R-D D-4 (T2-H10, T1-8): lo que el perfil público muestra como disponible: rangos unidos (D-2),
     * sin las reservas no canceladas y desde {@code ahora + VENTANA_MINIMA} alineado a 30 min. Un
     * día sin rangos libres no aparece.
     */
    public List<DiaDisponible> disponibilidad(UUID tutorId, int dias) {
        int n = Math.max(1, Math.min(dias, HORIZONTE_PROXIMO_LIBRE_DIAS));
        List<FranjaDisponibilidad> franjas = franjaService.franjasActivas(tutorId);
        if (franjas.isEmpty()) {
            return List.of();
        }
        LocalDate hoy = LocalDate.now(ReservasZonaHoraria.ZONA);
        List<Reserva> reservas = reservasEntre(tutorId, hoy, hoy.plusDays(n));
        Instant minimo = Instant.now().plus(VENTANA_MINIMA);
        List<DiaDisponible> resultado = new ArrayList<>();
        for (LocalDate fecha = hoy; fecha.isBefore(hoy.plusDays(n)); fecha = fecha.plusDays(1)) {
            ZonedDateTime medianoche = fecha.atStartOfDay(ReservasZonaHoraria.ZONA);
            List<RangoLibre> libres = new ArrayList<>();
            for (FranjaService.RangoMinutos r : FranjaService.unir(franjasDelDia(franjas, fecha))) {
                int desde = r.desde();
                while (desde < r.hasta() && medianoche.plusMinutes(desde).toInstant().isBefore(minimo)) {
                    desde += PASO_MINUTOS;
                }
                int cursor = desde;
                for (Reserva res : reservasOrdenadas(reservas, medianoche, r)) {
                    int ini = minutosDesde(medianoche, res.getHorario());
                    int fin = minutosDesde(medianoche, res.getHorarioFin());
                    if (ini > cursor) {
                        agregar(libres, cursor, Math.min(ini, r.hasta()));
                    }
                    cursor = Math.max(cursor, fin);
                }
                agregar(libres, cursor, r.hasta());
            }
            if (!libres.isEmpty()) {
                resultado.add(new DiaDisponible(fecha, libres));
            }
        }
        return resultado;
    }

    /**
     * R-D D-1: reservas no canceladas, futuras, del Tutor de la franja que caen dentro de ella.
     * Es solo informativo para la confirmación de la baja.
     */
    public long reservasFuturasEnLaFranja(FranjaDisponibilidad franja) {
        Instant ahora = Instant.now();
        return reservaRepo.findByTutor_IdAndEstadoNotAndHorarioBetween(franja.getTutor().getId(),
                        EstadoReserva.CANCELADA, ahora, ahora.plus(Duration.ofDays(3650))).stream()
                .filter(r -> {
                    ZonedDateTime local = r.getHorario().atZone(ReservasZonaHoraria.ZONA);
                    LocalTime hora = local.toLocalTime();
                    return FranjaService.aplicaA(franja, local.toLocalDate())
                            && !hora.isBefore(franja.getHoraInicio()) && hora.isBefore(franja.getHoraFin());
                })
                .count();
    }

    private static List<FranjaDisponibilidad> franjasDelDia(List<FranjaDisponibilidad> franjas, LocalDate fecha) {
        return franjas.stream().filter(f -> FranjaService.aplicaA(f, fecha)).toList();
    }

    private List<Reserva> reservasEntre(UUID tutorId, LocalDate desde, LocalDate hastaExclusive) {
        return reservaRepo.findByTutor_IdAndEstadoNotAndHorarioBetween(tutorId, EstadoReserva.CANCELADA,
                desde.atStartOfDay(ReservasZonaHoraria.ZONA).toInstant(),
                hastaExclusive.atStartOfDay(ReservasZonaHoraria.ZONA).toInstant());
    }

    private static List<Reserva> reservasOrdenadas(List<Reserva> reservas, ZonedDateTime medianoche,
                                                   FranjaService.RangoMinutos r) {
        return reservas.stream()
                .filter(res -> minutosDesde(medianoche, res.getHorario()) < r.hasta()
                        && minutosDesde(medianoche, res.getHorarioFin()) > r.desde())
                .sorted(java.util.Comparator.comparing(Reserva::getHorario))
                .toList();
    }

    private static int minutosDesde(ZonedDateTime medianoche, Instant instante) {
        return (int) Duration.between(medianoche.toInstant(), instante).toMinutes();
    }

    /** Agrega {@code [desde, hasta)} si alcanza para la clase más corta (30 min). */
    private static void agregar(List<RangoLibre> libres, int desde, int hasta) {
        if (hasta - desde >= PASO_MINUTOS) {
            libres.add(new RangoLibre(hhmm(desde), hhmm(hasta)));
        }
    }

    private static String hhmm(int minutos) {
        return String.format("%02d:%02d", minutos / 60, minutos % 60);
    }

    /** Mismo criterio que la EXCLUDE de V30 (AUD-009): rangos semiabiertos
     *  {@code [horario, horarioFin)} — las contiguas no se superponen. */
    private boolean seSuperponen(Reserva r, Instant inicioBloque, Instant finBloque) {
        return inicioBloque.isBefore(r.getHorarioFin()) && r.getHorario().isBefore(finBloque);
    }
}
