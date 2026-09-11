package com.tinku.matching;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

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

    /** Solo Tutor puede definir temas (contrato 2b). */
    @ExceptionHandler(TemasSoloTutorException.class)
    public ResponseEntity<Map<String, String>> handleSoloTutor(TemasSoloTutorException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    /** id de tema fuera del catálogo vigente (FR-MATCH-006). */
    @ExceptionHandler(TemaInexistenteException.class)
    public ResponseEntity<Map<String, String>> handleTemaInexistente(TemaInexistenteException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /** id no UUID, body malformado o búsqueda sin ningún campo (422). */
    @ExceptionHandler({TemaIdMalformadoException.class, BusquedaInvalidaException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleMalformado(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }
}