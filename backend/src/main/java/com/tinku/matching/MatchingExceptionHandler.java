package com.tinku.matching;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Traduce errores del módulo de matching a respuestas HTTP seguras (sin
 * exprimir detalle interno del proceso Python). */
@RestControllerAdvice(basePackages = "com.tinku.matching")
public class MatchingExceptionHandler {

    @ExceptionHandler(MatchingNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> handleNoDisponible(MatchingNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BusquedaGuardadaNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleNoEncontrada(BusquedaGuardadaNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}