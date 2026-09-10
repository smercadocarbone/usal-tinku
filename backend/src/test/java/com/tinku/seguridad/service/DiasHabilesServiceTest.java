package com.tinku.seguridad.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-SEC-010: el SLA de 5 días hábiles camina sobre fines de semana. */
class DiasHabilesServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private Instant en(String fechaHora) {
        return LocalDateTime.parse(fechaHora).atZone(ZONA).toInstant();
    }

    @Test
    void viernesMas5Habiles_SaltaElFinDeSemana() {
        // 2026-09-18 es viernes; 5 hábiles después = viernes 25 (sáb/dom no cuentan).
        assertThat(new DiasHabilesService().sumarDiasHabiles(en("2026-09-18T10:30:00")))
                .isEqualTo(en("2026-09-25T00:00:00"));
    }

    @Test
    void lunesMas5Habiles_CaeElLunesDeLaSemanaSiguiente() {
        // 2026-09-21 es lunes; 5 hábiles desde el martes → lunes 28 (sáb/dom no cuentan).
        assertThat(new DiasHabilesService().sumarDiasHabiles(en("2026-09-21T09:00:00")))
                .isEqualTo(en("2026-09-28T00:00:00"));
    }

    @Test
    void martesMas5Habiles_CaeElLunesSiguiente() {
        // 2026-09-22 es martes; 5 hábiles después = martes 29.
        assertThat(new DiasHabilesService().sumarDiasHabiles(en("2026-09-22T15:00:00")))
                .isEqualTo(en("2026-09-29T00:00:00"));
    }
}