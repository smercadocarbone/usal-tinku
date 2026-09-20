package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetearPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String passwordNueva
) {
}
