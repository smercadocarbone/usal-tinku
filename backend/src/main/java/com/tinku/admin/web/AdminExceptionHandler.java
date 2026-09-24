package com.tinku.admin.web;

import com.tinku.admin.service.OrigenMapNoDefinidoException;
import com.tinku.admin.service.TicketNoEncontradoException;
import com.tinku.identidad.service.CredencialNoPendienteException;
import com.tinku.shared.AccesoModeracionDenegadoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce errores del módulo admin a HTTP (mismo patrón que los demás módulos):
 *   - {@link AccesoModeracionDenegadoException} → 403 (también la emiten
 *     {@code ReputacionExceptionHandler} y {@code SeguridadExceptionHandler} para
 *     sus propios endpoints de admin — acá cubre los de {@code com.tinku.admin}).
 *   - {@link OrigenMapNoDefinidoException} → 422: el ticket no se enruta y no se
 *     persiste (fail-closed).
 *   - La validación de body ({@code MethodArgumentNotValidException}) → 400 es
 *     global, en {@code com.tinku.config.ValidacionErrorHandler}.
 */
@RestControllerAdvice(basePackages = "com.tinku.admin")
public class AdminExceptionHandler {

    @ExceptionHandler(AccesoModeracionDenegadoException.class)
    public ResponseEntity<Map<String, String>> accesoDenegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "No autorizado para esta acción de administración."));
    }

    @ExceptionHandler(OrigenMapNoDefinidoException.class)
    public ResponseEntity<Map<String, String>> origenSinMapeo(OrigenMapNoDefinidoException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
    }

    /** AUD-033: carrera entre el chequeo del controller y la transición del servicio. */
    @ExceptionHandler(CredencialNoPendienteException.class)
    public ResponseEntity<Map<String, String>> credencialNoPendiente(CredencialNoPendienteException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(TicketNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> ticketNoEncontrado(TicketNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}