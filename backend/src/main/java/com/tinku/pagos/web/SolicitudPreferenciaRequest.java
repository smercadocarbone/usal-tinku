package com.tinku.pagos.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SolicitudPreferenciaRequest(
        @NotNull(message = "reservaId es obligatorio.") UUID reservaId) {
}