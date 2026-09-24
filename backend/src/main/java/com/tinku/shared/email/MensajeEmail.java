package com.tinku.shared.email;

/** Email transaccional en texto plano (ADR-000-06). */
public record MensajeEmail(String para, String asunto, String texto) {
}
