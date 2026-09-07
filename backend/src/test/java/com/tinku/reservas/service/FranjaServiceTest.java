package com.tinku.reservas.service;

import com.tinku.reservas.model.FranjaDisponibilidad;
import com.tinku.reservas.repository.FranjaDisponibilidadRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Casos borde del Spec M4 sobre cobertura de franjas (FR-RES-012, ADR-M4-01):
 * la franja puntual y la semanal recurrente; hora_fin es exclusivo; día distinto
 * queda fuera; franja inactiva no cubre. La recurrencia semanal usa el formato
 * 0=domingo..6=sábado de V9.
 */
class FranjaServiceTest {

    private final FranjaDisponibilidadRepository franjaRepo = mock(FranjaDisponibilidadRepository.class);
    private final FranjaService servicio = new FranjaService(franjaRepo);

    private final UUID tutorId = UUID.randomUUID();

    private FranjaDisponibilidad franjaSemanal(short diaSemana, LocalTime inicio, LocalTime fin) {
        FranjaDisponibilidad f = new FranjaDisponibilidad();
        f.setDiaSemana(diaSemana);
        f.setFechaEspecifica(null);
        f.setHoraInicio(inicio);
        f.setHoraFin(fin);
        f.setActiva(true);
        return f;
    }

    private FranjaDisponibilidad franjaPuntual(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        FranjaDisponibilidad f = new FranjaDisponibilidad();
        f.setDiaSemana(null);
        f.setFechaEspecifica(fecha);
        f.setHoraInicio(inicio);
        f.setHoraFin(fin);
        f.setActiva(true);
        return f;
    }

    private Instant instante(int diaIso, int hora, int minuto) {
        return java.time.ZonedDateTime.of(LocalDate.now(ReservasZonaHoraria.ZONA)
                                .with(java.time.DayOfWeek.of(diaIso)),
                        LocalTime.of(hora, minuto), ReservasZonaHoraria.ZONA)
                .toInstant();
    }

    @Test
    void semanal_cubreHorarioDentroYExcluyeOtroDiaYHoraFinExclusivo() {
        when(franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId))
                .thenReturn(List.of(franjaSemanal((short) 3, LocalTime.of(9, 0), LocalTime.of(10, 0))));

        // Miércoles (diaIso 3) 09:30 dentro
        assertThat(servicio.estaDentroDeFranjaActiva(tutorId, instante(3, 9, 30))).isTrue();
        // Miércoles 10:00 fuera — hora_fin es exclusivo
        assertThat(servicio.estaDentroDeFranjaActiva(tutorId, instante(3, 10, 0))).isFalse();
        // Martes (diaIso 2) 09:30 fuera — otro día
        assertThat(servicio.estaDentroDeFranjaActiva(tutorId, instante(2, 9, 30))).isFalse();
    }

    @Test
    void semanal_domingoEnFormato0A6_domingoIso7Cubre() {
        when(franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId))
                .thenReturn(List.of(franjaSemanal((short) 0, LocalTime.of(9, 0), LocalTime.of(10, 0))));

        assertThat(servicio.estaDentroDeFranjaActiva(tutorId, instante(7, 9, 30))).isTrue();
    }

    @Test
    void puntual_soloEseDia() {
        LocalDate hoy = LocalDate.now(ReservasZonaHoraria.ZONA);
        when(franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId))
                .thenReturn(List.of(franjaPuntual(hoy.plusDays(2), LocalTime.of(15, 0), LocalTime.of(16, 0))));

        Instant dentro = java.time.ZonedDateTime.of(hoy.plusDays(2), LocalTime.of(15, 30),
                ReservasZonaHoraria.ZONA).toInstant();
        assertThat(servicio.estaDentroDeFranjaActiva(tutorId, dentro)).isTrue();

        Instant otroDia = java.time.ZonedDateTime.of(hoy.plusDays(1), LocalTime.of(15, 30),
                ReservasZonaHoraria.ZONA).toInstant();
        assertThat(servicio.estaDentroDeFranjaActiva(tutorId, otroDia)).isFalse();
    }

    @Test
    void franjaInactiva_noCubre() {
        when(franjaRepo.findByTutorIdAndActivaTrueOrderByHoraInicio(tutorId)).thenReturn(List.of());
        assertThat(servicio.estaDentroDeFranjaActiva(tutorId, instante(3, 9, 30))).isFalse();
    }
}