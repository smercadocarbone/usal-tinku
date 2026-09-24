package com.tinku.identidad.dto;

import com.tinku.identidad.validacion.PasswordSegura;
import jakarta.validation.constraints.NotBlank;

public record ResetearPasswordRequest(
        @NotBlank String token,
        @NotBlank @PasswordSegura String passwordNueva
) {
}
