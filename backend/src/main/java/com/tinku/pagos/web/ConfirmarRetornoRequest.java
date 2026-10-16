package com.tinku.pagos.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Retorno de MercadoPago: la reserva y el {@code payment_id} que MP agregó a la URL. */
public record ConfirmarRetornoRequest(@NotNull UUID reservaId, @NotBlank @jakarta.validation.constraints.Pattern(regexp = "\\d{1,20}", message = "El número de pago no es válido.") String paymentId) {
}
