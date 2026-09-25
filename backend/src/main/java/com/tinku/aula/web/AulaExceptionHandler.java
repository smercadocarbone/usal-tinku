package com.tinku.aula.web;

import com.tinku.aula.AlertaNoEncontradaException;
import com.tinku.aula.ConfirmacionNoPendienteException;
import com.tinku.aula.DetectadoInvalidoException;
import com.tinku.aula.EvidenciaInvalidaException;
import com.tinku.aula.AudioResumenInvalidoException;
import com.tinku.aula.SesionCerradaException;
import com.tinku.aula.SesionNoEncontradaException;
import com.tinku.aula.SesionSinSalaException;
import com.tinku.aula.SoloParticipanteException;
import com.tinku.reservas.service.ReservaNoEncontradaException;
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

    /**
     * Ya se usaba dentro del módulo (obtenerToken/finalizar resuelven la Reserva
     * de la Sesión) sin tener handler propio acá — un 500 en vez de 404 en el
     * caso borde de una Reserva inexistente. GET /sesiones/por-reserva la
     * dispara desde el otro sentido (reservaId como input directo del cliente),
     * así que el caso deja de ser borde y hay que responderlo bien.
     */
    @ExceptionHandler(ReservaNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handleReservaNoEncontrada(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SoloParticipanteException.class)
    public ResponseEntity<Map<String, String>> handleProhibido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({SesionSinSalaException.class, SesionCerradaException.class,
            DetectadoInvalidoException.class,
            ConfirmacionNoPendienteException.class,
            EvidenciaInvalidaException.class,
            AudioResumenInvalidoException.class})
    public ResponseEntity<Map<String, String>> handleInvalido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }
}