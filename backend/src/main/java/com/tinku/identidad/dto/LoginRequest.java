package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String dni,
        @NotBlank String password
) {}
