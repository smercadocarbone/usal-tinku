package com.tinku.pagos.web;

import com.tinku.pagos.service.MercadoPagoNoConfiguradoException;
import com.tinku.pagos.service.MercadoPagoNoDisponibleException;
import com.tinku.pagos.service.PreferenciaNoDisponibleException;
import com.tinku.pagos.service.SoloPagadorPreferenciaException;
import com.tinku.reservas.service.ReservaNoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Traduce errores del módulo de pagos a HTTP (mismo estilo que ReservasExceptionHandler). */
@RestControllerAdvice(basePackages = "com.tinku.pagos")
public class PagoExceptionHandler {

    @ExceptionHandler(SoloPagadorPreferenciaException.class)
    public ResponseEntity<Map<String, String>> handleProhibido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PreferenciaNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> handleRegla(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReservaNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleNoEncontrada(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({MercadoPagoNoConfiguradoException.class, MercadoPagoNoDisponibleException.class})
    public ResponseEntity<Map<String, String>> handleMercadoPago(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidacion(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error",
                ex.getBindingResult().getFieldErrors().getFirst().getDefaultMessage()));
    }
}