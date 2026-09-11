package com.tinku.seguridad.service;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * FR-SEC-010: SLA de resolución = 5 DÍAS HÁBILES desde el vencimiento del
 * descargo. Se saltan sábados y domingos; los feriados argentinos NO se
 * descuentan (decisión de T-M9-03, documentada aquí — el costo de un feriado
 * sin descontar es resolver un día antes, nunca un día después, y no se suma
 * una tabla de feriados que cambia cada año). El resultado cae al inicio del
 * día hábil (medianoche de Buenos Aires) — la ventana real de revisión del
 * Admin es `[descargo_vence_at, sla_resolucion_vence_at)` para el job de
 * escalado.
 */
@Component
public class DiasHabilesService {

    /** Zona de negocio: horario de Buenos Aires (Artículo X, jurisdicción AR). */
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    static final int DIAS_DE_SLA = 5;

    public Instant sumarDiasHabiles(Instant desde) {
        LocalDate dia = desde.atZone(ZONA).toLocalDate();
        int restantes = DIAS_DE_SLA;
        while (restantes > 0) {
            dia = dia.plus(1, ChronoUnit.DAYS);
            if (esHabil(dia)) {
                restantes--;
            }
        }
        return dia.atStartOfDay(ZONA).toInstant();
    }

    private static boolean esHabil(LocalDate dia) {
        return dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}