package com.tinku.reputacion.web;

import jakarta.validation.constraints.Size;

/** El tope del comentario es el de la columna (VARCHAR(1000), V14): pasarse es 400, no un 500 de la base. */
public record CalificarRequest(
        Short estrellas,
        @Size(max = 1000, message = "El comentario puede tener hasta 1000 caracteres.") String comentario) {
}
