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

    @ExceptionHandler(OcrNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> handleOcrNoDisponible(OcrNoDisponibleException ex) {
        // 503: el proveedor de OCR falló en sí mismo (no es culpa del usuario
        // ni de la foto) — no consume los reintentos del ciclo FR-ID-011.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
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

    @ExceptionHandler(CredencialEnBackoffException.class)
    public ResponseEntity<Map<String, String>> handleCredencialBackoff(CredencialEnBackoffException ex) {
        // 429: ciclo de credencial agotado, espera escalada 24→48→96… (FR-ID-012).
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage(),
                        "espera_restante_hs", String.valueOf(ex.getEsperaRestante().toHours())));
    }

    @ExceptionHandler(YaExisteCredencialPendienteException.class)
    public ResponseEntity<Map<String, String>> handleCredencialPendiente(YaExisteCredencialPendienteException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CredencialNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleCredencialNoEncontrada(CredencialNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MenorNoPerteneceException.class)
    public ResponseEntity<Map<String, String>> handleMenorNoPertenece(MenorNoPerteneceException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TutorNoAutorizadoException.class)
    public ResponseEntity<Map<String, String>> handleTutorNoAutorizado(TutorNoAutorizadoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TutorNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleTutorNoEncontrado(TutorNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ReservasFuturasPendientesException.class)
    public ResponseEntity<Map<String, String>> handleReservasFuturas(ReservasFuturasPendientesException ex) {
        // FR-ID-014: 409 — pedir confirmación explícita con la cantidad.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage(),
                        "reservas_futuras", String.valueOf(ex.getCantidadReservas())));
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciales inválidas"));
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    public ResponseEntity<Map<String, String>> handleCuentaSuspendida() {
        // Auditoría 2026-09-18 (AuthService.login): 403, no 401 — las
        // credenciales SON correctas, lo que falta es la cuenta activa.
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Cuenta suspendida. Contactá a soporte para más información."));
    }
}
