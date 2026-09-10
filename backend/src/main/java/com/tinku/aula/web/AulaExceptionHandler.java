package com.tinku.aula.web;

import com.tinku.aula.AlertaNoEncontradaException;
import com.tinku.aula.ConfirmacionNoPendienteException;
import com.tinku.aula.DetectadoInvalidoException;
import com.tinku.aula.EvidenciaInvalidaException;
import com.tinku.aula.SesionNoEncontradaException;
import com.tinku.aula.SoloParticipanteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce errores del módulo de Aula a HTTP (mismo patrón que
 * ReservasExceptionHandler, scoped al paquete {@code com.tinku.aula}).
 */
@RestControllerAdvice(basePackages = "com.tinku.aula")
public class AulaExceptionHandler {

    @ExceptionHandler(SesionNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleNoEncontrada(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AlertaNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleAlertaNoEncontrada(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SoloParticipanteException.class)
    public ResponseEntity<Map<String, String>> handleProhibido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({DetectadoInvalidoException.class,
            ConfirmacionNoPendienteException.class,
            EvidenciaInvalidaException.class})
    public ResponseEntity<Map<String, String>> handleInvalido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }
}