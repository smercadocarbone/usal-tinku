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
        // R-D D-3 (T2-H2): una franja puntual de un día que ya pasó no se puede reservar nunca.
        if (request.fechaEspecifica() != null
                && request.fechaEspecifica().isBefore(LocalDate.now(ReservasZonaHoraria.ZONA))) {
            throw new FechaFranjaPasadaException();
        }
        if (!request.horaFin().isAfter(request.horaInicio())) {
            throw new HorarioFueraDeFranjaException("La hora de fin tiene que ser posterior a la de inicio.");
        }
        long minutos = Duration.between(request.horaInicio(), request.horaFin()).toMinutes();
        if (minutos < DURACION_MINIMA.toMinutes() || minutos > DURACION_MAXIMA.toMinutes()) {
            throw new DuracionFranjaInvalidaException(
                    "El horario tiene que durar de 30 a 180 minutos.");
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

    /** Rango {@code [desde, hasta)} de un día, en minutos desde la medianoche. */
    public record RangoMinutos(int desde, int hasta) {
    }

    /**
     * R-D D-2 (T1-M2, T1-M3): rangos del día con las franjas contiguas o superpuestas unidas,
     * ordenados por {@code desde}. Todo en minutos enteros: no hay aritmética de {@code LocalTime}
     * que pueda dar la vuelta a la medianoche.
     */
    public List<RangoMinutos> rangosUnidos(UUID tutorId, LocalDate fecha) {
        return unir(franjasQueAplicanA(tutorId, fecha));
    }

    /** Une franjas de un mismo día (ver {@link #rangosUnidos}). */
    public static List<RangoMinutos> unir(List<FranjaDisponibilidad> franjasDelDia) {
        List<RangoMinutos> ordenados = franjasDelDia.stream()
                .map(f -> new RangoMinutos(minutos(f.getHoraInicio()), minutos(f.getHoraFin())))
                .sorted(java.util.Comparator.comparingInt(RangoMinutos::desde))
                .toList();
        List<RangoMinutos> unidos = new java.util.ArrayList<>();
        for (RangoMinutos r : ordenados) {
            RangoMinutos ultimo = unidos.isEmpty() ? null : unidos.get(unidos.size() - 1);
            if (ultimo != null && r.desde() <= ultimo.hasta()) {
                unidos.set(unidos.size() - 1, new RangoMinutos(ultimo.desde(), Math.max(ultimo.hasta(), r.hasta())));
            } else {
                unidos.add(r);
            }
        }
        return unidos;
    }

    static int minutos(LocalTime hora) {
        return hora.toSecondOfDay() / 60;
    }

    /**
     * D6 reglas 2 y 3 (con R-D D-2): {@code [inicio, inicio+duracion)} entra entero en un rango
     * unido de disponibilidad y {@code inicio} está alineado a un bloque de 30 minutos desde el
     * comienzo de ese rango.
     */
    public boolean cabeEnDisponibilidad(UUID tutorId, Instant inicio, int duracionMinutos) {
        if (!duracionValida(duracionMinutos)) {
            return false;
        }
        LocalDateTime local = inicio.atZone(ReservasZonaHoraria.ZONA).toLocalDateTime();
        if (local.getSecond() != 0 || local.getNano() != 0) {
            return false;
        }
        int inicioMin = minutos(local.toLocalTime());
        return rangosUnidos(tutorId, local.toLocalDate()).stream()
                .anyMatch(r -> inicioMin >= r.desde()
                        && inicioMin + duracionMinutos <= r.hasta()
                        && (inicioMin - r.desde()) % 30 == 0);
    }

    /**
     * R-D D-1 (T2-H1, FR-RES-025): baja lógica de una franja propia. No toca reservas existentes:
     * guardan su horario absoluto. 404 si no existe, ya está inactiva o es de otro Tutor (no se
     * revela la existencia de franjas ajenas).
     */
    @Transactional
    public FranjaDisponibilidad quitar(Usuario tutor, UUID franjaId) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException("Solo las cuentas de Tutor pueden quitar disponibilidad.");
        }
        FranjaDisponibilidad franja = franjaRepo.findById(franjaId)
                .filter(FranjaDisponibilidad::isActiva)
                .filter(f -> f.getTutor().getId().equals(tutor.getId()))
                .orElseThrow(FranjaNoEncontradaException::new);
        franja.setActiva(false);
        return franjaRepo.save(franja);
    }

    /** Franjas activas publicadas por el Tutor (GET /api/tutores/{id}/franjas). */
    public List<FranjaDisponibilidad> franjasActivas(UUID tutorId) {
        return franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId);
    }

    /**
     * Devuelve la franja activa que cubre {@code horario}, si existe (desde AUD-020 la
     * duración sale de la Reserva, no de la franja).
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
        return franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId).stream()
                .filter(f -> aplicaA(f, fecha))
                .toList();
    }

    /** ¿La franja (semanal o puntual) aplica a esa fecha del calendario? */
    static boolean aplicaA(FranjaDisponibilidad f, LocalDate fecha) {
        short diaDomingoCero = (short) (fecha.getDayOfWeek().getValue() % 7);
        return f.getFechaEspecifica() != null
                ? f.getFechaEspecifica().equals(fecha)
                : f.getDiaSemana() != null && f.getDiaSemana() == diaDomingoCero;
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