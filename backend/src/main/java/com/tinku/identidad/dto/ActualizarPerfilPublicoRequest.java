package com.tinku.identidad.dto;

/** U1: bio del perfil público del Tutor. {@code null} o vacía = sin bio. El largo lo valida el servicio. */
public record ActualizarPerfilPublicoRequest(String bio) {
}
