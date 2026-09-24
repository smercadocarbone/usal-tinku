package com.tinku.identidad.dto;

import com.tinku.identidad.validacion.PasswordSegura;
import jakarta.validation.constraints.NotBlank;

/** Cambio de contraseña del usuario autenticado ("editar cuenta"). Exige la
 * contraseña actual — no alcanza con estar logueado (sesión pudo quedar
 * abierta en un dispositivo compartido). */
public record CambiarPasswordRequest(
        @NotBlank String passwordActual,
        @NotBlank @PasswordSegura String passwordNueva
) {
}
