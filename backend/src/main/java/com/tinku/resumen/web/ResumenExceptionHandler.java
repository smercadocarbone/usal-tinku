package com.tinku.resumen.web;

import com.tinku.resumen.service.ResumenNoPermitidoException;
import com.tinku.resumen.service.ResumenSesionNoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Traduce errores del módulo de resumen a HTTP (scoped a com.tinku.resumen). */
@RestControllerAdvice(basePackages = "com.tinku.resumen")
public class ResumenExceptionHandler {

    @ExceptionHandler(ResumenSesionNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleNoEncontrada(ResumenSesionNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResumenNoPermitidoException.class)
    public ResponseEntity<Map<String, String>> handleProhibido(ResumenNoPermitidoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}
