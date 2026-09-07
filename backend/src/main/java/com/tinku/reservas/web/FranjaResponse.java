package com.tinku.reservas.web;

import com.tinku.reservas.model.FranjaDisponibilidad;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record FranjaResponse(UUID id, UUID tutorId, Short diaSemana, LocalDate fechaEspecifica,
                             LocalTime horaInicio, LocalTime horaFin, boolean activa) {

    public static FranjaResponse from(FranjaDisponibilidad f) {
        return new FranjaResponse(f.getId(), f.getTutor().getId(), f.getDiaSemana(),
                f.getFechaEspecifica(), f.getHoraInicio(), f.getHoraFin(), f.isActiva());
    }
}