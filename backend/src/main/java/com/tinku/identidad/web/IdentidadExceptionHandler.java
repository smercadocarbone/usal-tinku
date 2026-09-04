package com.tinku.identidad.web;

import com.tinku.identidad.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce las excepciones de dominio de M1 a respuestas HTTP con mensajes
 * seguros para mostrar al usuario final — ninguna de estas expone detalle
 * interno (ej. FR-ID-018: nunca decir de quién es el DNI duplicado).
 */
@RestControllerAdvice(basePackages = "com.tinku.identidad")
public class IdentidadExceptionHandler {

    @ExceptionHandler(DniYaRegistradoException.class)
    public ResponseEntity<Map<String, String>> handleDniDuplicado(DniYaRegistradoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentoNoCoincideException.class)
    public ResponseEntity<Map<String, String>> handleNoCoincide(DocumentoNoCoincideException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(EdadInsuficienteException.class)
    public ResponseEntity<Map<String, String>> handleEdadInsuficiente(EdadInsuficienteException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentoIlegibleException.class)
    public ResponseEntity<Map<String, String>> handleIlegible(DocumentoIlegibleException ex) {
        // El contador de intentos del ciclo de backoff (FR-ID-011) ya se
        // incrementó en UsuarioService/OcrBackoffService antes de lanzar.
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DocumentoEnBackoffException.class)
    public ResponseEntity<Map<String, String>> handleBackoff(DocumentoEnBackoffException ex) {
        // 429: el cliente agotó los 3 intentos del ciclo y está en espera (FR-ID-011).
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage(),
                        "espera_restante_hs", String.valueOf(ex.getEsperaRestante().toHours())));
    }

    @ExceptionHandler(ConsentimientoNoOtorgadoException.class)
    public ResponseEntity<Map<String, String>> handleConsentimiento(ConsentimientoNoOtorgadoException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(LimiteMenoresAlcanzadoException.class)
    public ResponseEntity<Map<String, String>> handleLimiteMenores(LimiteMenoresAlcanzadoException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NoPuedeDesactivarAdultoResponsableException.class)
    public ResponseEntity<Map<String, String>> handleNoDesactivarResponsable(NoPuedeDesactivarAdultoResponsableException ex) {
        // FR-ID-016: 409 conflict — no puede dejar de ser Adulto Responsable
        // con menores a cargo.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleArgumentoInvalido(IllegalArgumentException ex) {
        // Violaciones de FR-ID-001/015/artículo II en capacidades (p.ej. quedar
        // sin capacidades o un menor intentando ser Adulto Responsable).
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciales inválidas"));
    }
}
