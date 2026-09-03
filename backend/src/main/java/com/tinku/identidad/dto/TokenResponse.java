package com.tinku.identidad.dto;

public record TokenResponse(
        String token,
        String tipo,
        long expiresInMinutes
) {}
