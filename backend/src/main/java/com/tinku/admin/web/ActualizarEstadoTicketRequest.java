package com.tinku.admin.web;

import com.tinku.admin.model.EstadoTicket;
import jakarta.validation.constraints.NotNull;

public record ActualizarEstadoTicketRequest(@NotNull EstadoTicket estado) {
}
