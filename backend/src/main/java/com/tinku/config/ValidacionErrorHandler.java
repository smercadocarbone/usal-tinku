package com.tinku.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validación de body de cualquier DTO con {@code @Valid}, en un solo lugar
 * (UX-02 B2 / AUD-034). Antes, un cuerpo inválido podía terminar en 403
 * ("no autorizado"): el {@code DefaultHandlerExceptionResolver} reenviaba a
 * {@code /error} y la cadena de seguridad lo cortaba — un error de
 * validación no puede ser "no autorizado".
 *
 * Respuesta: 400 con {@code {"error": "campo: mensaje", "campos": {...}}}.
 * Cada {@code campos[campo]} es el primer mensaje de ese campo, para que la
 * UI lo muestre junto al input (el cliente expone {@code campos} como
 * {@code detalles}).
 *
 * Los ExceptionHandler scoped por módulo (reservas, admin, pagos, reputacion)
 * también traducían {@code MethodArgumentNotValidException} cada uno a su
 * manera; acá se centralizó el caso para que el contrato 400 sea uniforme en
 * toda la app.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ValidacionErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String primero = campos.entrySet().iterator().next().getKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", primero + ": " + campos.get(primero));
        body.put("campos", campos);
        return ResponseEntity.badRequest().body(body);
    }
}