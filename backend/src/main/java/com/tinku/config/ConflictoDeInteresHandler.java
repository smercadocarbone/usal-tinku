package com.tinku.config;

import com.tinku.shared.ConflictoDeInteresException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * R-F F-1 (T4-H2): el conflicto de interés se responde igual en todos los módulos. Va primero
 * (HIGHEST_PRECEDENCE) porque cada módulo ya mapea la clase base, AccesoModeracionDenegadoException,
 * con el mensaje de "no tenés el rol".
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConflictoDeInteresHandler {

    @ExceptionHandler(ConflictoDeInteresException.class)
    public ResponseEntity<Map<String, String>> conflicto(ConflictoDeInteresException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage(), "codigo", ConflictoDeInteresException.CODIGO));
    }
}
