package com.tinku.aula.web;

import jakarta.validation.constraints.NotNull;

public record EventoClasificadorRequest(@NotNull EventoClasificador evento) {
}
