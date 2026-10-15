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

    /** TRANS-1: JSON mal formado, tipos que no se pueden leer, cuerpo vacío. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleCuerpoIlegible(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return error(org.springframework.http.HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido.");
    }

    /** TRANS-1: un parámetro de ruta o de query con un tipo que no corresponde (UUID, enum, fecha). */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoInvalido(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        String mensaje = "El parámetro «" + ex.getName() + "» no es válido.";
        Class<?> tipo = ex.getRequiredType();
        if (tipo != null && tipo.isEnum()) {
            mensaje += " Valores aceptados: " + java.util.Arrays.stream(tipo.getEnumConstants())
                    .map(Object::toString).collect(java.util.stream.Collectors.joining(", ")) + ".";
        }
        return error(org.springframework.http.HttpStatus.BAD_REQUEST, mensaje);
    }

    /** TRANS-1: falta un parámetro de query obligatorio. */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleParametroFaltante(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {
        return error(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Falta el parámetro «" + ex.getParameterName() + "».");
    }

    /** TRANS-1: falta una parte de un multipart. */
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleParteFaltante(
            org.springframework.web.multipart.support.MissingServletRequestPartException ex) {
        return error(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Falta el archivo «" + ex.getRequestPartName() + "».");
    }

    /** TRANS-1: archivo más grande que spring.servlet.multipart.max-file-size. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleArchivoGrande(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return error(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido.");
    }

    private static ResponseEntity<Map<String, Object>> error(org.springframework.http.HttpStatus status, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}
