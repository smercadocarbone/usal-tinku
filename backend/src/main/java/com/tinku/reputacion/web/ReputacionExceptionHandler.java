package com.tinku.reputacion.web;

import com.tinku.reputacion.service.CalificacionDefinitivaException;
import com.tinku.reputacion.service.CalificacionNoEncontradaException;
import com.tinku.reputacion.service.CalificacionNoPermitidaException;
import com.tinku.reputacion.service.CalificacionOcultaNoEditableException;
import com.tinku.reputacion.service.CalificacionSesionNoEncontradaException;
import com.tinku.reputacion.service.CalificacionYaExisteException;
import com.tinku.reputacion.service.ComentarioNoPermitidoException;
import com.tinku.reputacion.service.EstrellasInvalidasException;
import com.tinku.reputacion.service.SesionNoFinalizadaException;
import com.tinku.shared.AccesoModeracionDenegadoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Traduce errores del modulo de reputacion a HTTP (scoped a com.tinku.reputacion). */
@RestControllerAdvice(basePackages = "com.tinku.reputacion")
public class ReputacionExceptionHandler {

    @ExceptionHandler({CalificacionNoPermitidaException.class, AccesoModeracionDenegadoException.class,
            CalificacionOcultaNoEditableException.class})
    public ResponseEntity<Map<String, String>> handleProhibido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({SesionNoFinalizadaException.class, CalificacionYaExisteException.class,
            EstrellasInvalidasException.class, ComentarioNoPermitidoException.class,
            CalificacionDefinitivaException.class})
    public ResponseEntity<Map<String, String>> handleRegla(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({CalificacionSesionNoEncontradaException.class, CalificacionNoEncontradaException.class})
    public ResponseEntity<Map<String, String>> handleNoEncontrada(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}