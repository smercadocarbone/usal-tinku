package com.tinku.reservas.web;

import com.tinku.reservas.service.BeneficiarioNoPerteneceException;
import com.tinku.reservas.service.CapacidadDePagoRequeridaException;
import com.tinku.reservas.service.DuracionFranjaInvalidaException;
import com.tinku.reservas.service.HorarioFueraDeFranjaException;
import com.tinku.reservas.service.NoPuedeCancelarReservaException;
import com.tinku.reservas.service.ReservaNoCancelableException;
import com.tinku.reservas.service.ReservaNoEncontradaException;
import com.tinku.reservas.service.ReservaNoReprogramableException;
import com.tinku.reservas.service.SoloAdultoResponsableException;
import com.tinku.reservas.service.SoloMenorException;
import com.tinku.reservas.service.SoloPagadorReservaException;
import com.tinku.reservas.service.SoloTutorException;
import com.tinku.reservas.service.SolicitudDuplicadaException;
import com.tinku.reservas.service.SolicitudMenorNoPerteneceException;
import com.tinku.reservas.service.SolicitudNoPendienteException;
import com.tinku.reservas.service.TarifaNoConfiguradaException;
import com.tinku.reservas.service.TutorNoAutorizadoParaMenorException;
import com.tinku.reservas.service.TutorNoEncontradoException;
import com.tinku.reservas.service.VentanaMinimaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduce errores del módulo de reservas a HTTP. Incluye la superposición de
 * horarios: la EXCLUDE constraint de V9 (FR-RES-007) violada se traduce a 409
 * "horario ocupado" — no se filtra el detalle interno de la constraint ni de
 * la base.
 */
@RestControllerAdvice(basePackages = "com.tinku.reservas")
public class ReservasExceptionHandler {

    @ExceptionHandler({SoloAdultoResponsableException.class, SoloMenorException.class,
            SoloTutorException.class, SolicitudMenorNoPerteneceException.class,
            TutorNoAutorizadoParaMenorException.class, CapacidadDePagoRequeridaException.class,
            BeneficiarioNoPerteneceException.class, SoloPagadorReservaException.class,
            NoPuedeCancelarReservaException.class})
    public ResponseEntity<Map<String, String>> handleProhibido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({HorarioFueraDeFranjaException.class, VentanaMinimaException.class,
            DuracionFranjaInvalidaException.class, ReservaNoReprogramableException.class,
            ReservaNoCancelableException.class})
    public ResponseEntity<Map<String, String>> handleRegla(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({SolicitudNoPendienteException.class, ReservaNoEncontradaException.class,
            TutorNoEncontradoException.class})
    public ResponseEntity<Map<String, String>> handleNoEncontrada(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SolicitudDuplicadaException.class)
    public ResponseEntity<Map<String, String>> handleDuplicada(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidacion(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error",
                ex.getBindingResult().getFieldErrors().getFirst().getDefaultMessage()));
    }

    /** FR-RES-007: la constraint EXCLUDE de V9 ganó la condición de carrera. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleSuperposicion(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El horario ya está reservado para ese Tutor o beneficiario (FR-RES-007)."));
    }

    @ExceptionHandler(TarifaNoConfiguradaException.class)
    public ResponseEntity<Map<String, String>> handleSinTarifa(TarifaNoConfiguradaException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage()));
    }
}