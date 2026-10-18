package com.tinku.admin.evaluacion;

import com.tinku.shared.evaluacion.EvaluacionDeshabilitadaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** T-TES-11b: errores del NPS y del panel de evaluación. */
@RestControllerAdvice(basePackages = "com.tinku.admin.evaluacion")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EvaluacionExceptionHandler {

    @ExceptionHandler(EvaluacionDeshabilitadaException.class)
    public ResponseEntity<Map<String, String>> deshabilitada(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NpsYaRespondidoException.class)
    public ResponseEntity<Map<String, String>> yaRespondido(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({NpsPuntajeInvalidoException.class, IndicadoresPeriodoInvalidoException.class})
    public ResponseEntity<Map<String, String>> invalido(RuntimeException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NpsNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> noDisponible(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> ilegible() {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", "Elegí un número del 0 al 10."));
    }

    @ExceptionHandler(com.tinku.shared.AccesoModeracionDenegadoException.class)
    public ResponseEntity<Map<String, String>> accesoDenegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "No autorizado para esta acción de administración."));
    }
}
