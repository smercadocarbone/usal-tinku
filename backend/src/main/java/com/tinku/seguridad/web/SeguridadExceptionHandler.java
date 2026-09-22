package com.tinku.seguridad.web;

import com.tinku.seguridad.AlertaSeguridadNoEncontradaException;
import com.tinku.seguridad.AlertaYaResueltaException;
import com.tinku.seguridad.AutoDenunciaException;
import com.tinku.seguridad.CasoNoEncontradoException;
import com.tinku.seguridad.DenunciaNoEncontradaException;
import com.tinku.seguridad.DenunciaYaResueltaException;
import com.tinku.seguridad.DescargoInvalidoException;
import com.tinku.seguridad.MenorNoDenunciaException;
import com.tinku.seguridad.NoParticipanteDenunciaException;
import com.tinku.seguridad.SancionInvalidaException;
import com.tinku.seguridad.SoloParteInteresadaException;
import com.tinku.shared.AccesoModeracionDenegadoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce errores del módulo de Seguridad a HTTP (mismo patrón que
 * ReservasExceptionHandler/AulaExceptionHandler, scoped a {@code com.tinku.seguridad}).
 * El 403 de moderación también se mapea acá porque lo lanzan los controllers de
 * M9 (M7 tendrá el suyo cuando use el gate).
 */
@RestControllerAdvice(basePackages = "com.tinku.seguridad")
public class SeguridadExceptionHandler {

    @ExceptionHandler({MenorNoDenunciaException.class, SoloParteInteresadaException.class,
            NoParticipanteDenunciaException.class,
            AccesoModeracionDenegadoException.class})
    public ResponseEntity<Map<String, String>> handleProhibido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({DenunciaNoEncontradaException.class, AlertaSeguridadNoEncontradaException.class,
            CasoNoEncontradoException.class})
    public ResponseEntity<Map<String, String>> handleNoEncontrado(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({DenunciaYaResueltaException.class, AlertaYaResueltaException.class,
            DescargoInvalidoException.class, SancionInvalidaException.class,
            AutoDenunciaException.class})
    public ResponseEntity<Map<String, String>> handleOperacionInvalida(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }
}