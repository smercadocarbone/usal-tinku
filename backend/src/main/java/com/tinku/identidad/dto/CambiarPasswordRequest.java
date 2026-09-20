package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambio de contraseña del usuario autenticado ("editar cuenta"). Exige la
 * contraseña actual — no alcanza con estar logueado (sesión pudo quedar
 * abierta en un dispositivo compartido). */
public record CambiarPasswordRequest(
        @NotBlank String passwordActual,
        @NotBlank @Size(min = 8) String passwordNueva
) {
}
