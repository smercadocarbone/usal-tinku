package com.tinku.reservas.web;

import com.tinku.reservas.service.BeneficiarioNoPerteneceException;
import com.tinku.reservas.service.CapacidadDePagoRequeridaException;
import com.tinku.reservas.service.DuracionFranjaInvalidaException;
import com.tinku.reservas.service.FranjaSuperpuestaException;
import com.tinku.reservas.service.TutorSinHabilitacionMenoresException;
import com.tinku.reservas.service.DuracionMinutosInvalidaException;
import com.tinku.reservas.service.HorarioFueraDeFranjaException;
import com.tinku.reservas.service.NoPuedeCancelarReservaException;
import com.tinku.reservas.service.ReservaNoCancelableException;
import com.tinku.reservas.service.ReservaNoEncontradaException;
import com.tinku.reservas.service.ReservaNoReprogramableException;
import com.tinku.reservas.service.SoloAdultoResponsableException;
import com.tinku.reservas.service.SoloMenorException;
import com.tinku.reservas.service.SoloPagadorReservaException;
import com.tinku.reservas.service.SoloTutorException;
import com.tinku.reservas.service.SesionesConMenoresDeshabilitadasException;
import com.tinku.reservas.service.SolicitudDuplicadaException;
import com.tinku.reservas.service.SolicitudMenorNoPerteneceException;
import com.tinku.reservas.service.SolicitudNoPendienteException;
import com.tinku.reservas.service.TarifaNoConfiguradaException;
import com.tinku.reservas.service.TutorNoAutorizadoParaMenorException;
import com.tinku.reservas.service.TutorNoEncontradoException;
import com.tinku.reservas.service.TutorPendienteCalificacionException;
import com.tinku.reservas.service.VentanaMinimaException;
import com.tinku.reservas.service.AdicionalResumenNoDisponibleException;
import com.tinku.reservas.service.AutoReservaNoPermitidaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ReservasExceptionHandler.class);

    @ExceptionHandler({SoloAdultoResponsableException.class, SoloMenorException.class,
            SoloTutorException.class, SolicitudMenorNoPerteneceException.class,
            TutorNoAutorizadoParaMenorException.class, CapacidadDePagoRequeridaException.class,
            BeneficiarioNoPerteneceException.class, SoloPagadorReservaException.class,
            NoPuedeCancelarReservaException.class, TutorPendienteCalificacionException.class})
    public ResponseEntity<Map<String, String>> handleProhibido(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({HorarioFueraDeFranjaException.class, VentanaMinimaException.class,
            DuracionFranjaInvalidaException.class, DuracionMinutosInvalidaException.class,
            ReservaNoReprogramableException.class, ReservaNoCancelableException.class,
            AdicionalResumenNoDisponibleException.class, AutoReservaNoPermitidaException.class})
    public ResponseEntity<Map<String, String>> handleRegla(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({SolicitudNoPendienteException.class, ReservaNoEncontradaException.class,
            TutorNoEncontradoException.class})
    public ResponseEntity<Map<String, String>> handleNoEncontrada(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /** 409 con el copy del dominio: solicitud duplicada (FR-RES-019) o gate del piloto (T-TES-10). */
    @ExceptionHandler({SolicitudDuplicadaException.class, SesionesConMenoresDeshabilitadasException.class,
            TutorSinHabilitacionMenoresException.class})
    public ResponseEntity<Map<String, String>> handleConflicto(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    /** FR-RES-007: la constraint EXCLUDE de V9 ganó la condición de carrera. */
    /**
     * AUD-023: solo la superposición de reservas (EXCLUDE {@code ex_reservas_rango_*}, V30)
     * es "horario ocupado". Cualquier otra violación de integridad (FK, CHECK) era un bug
     * que se disfrazaba de 409: ahora es 500 con log, sin filtrar el detalle al cliente.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleSuperposicion(DataIntegrityViolationException ex) {
        String constraint = constraintVioladaDe(ex);
        if (constraint != null && constraint.startsWith("ex_reservas_rango_")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El horario ya está reservado para ese Tutor o beneficiario (FR-RES-007)."));
        }
        if (constraint != null && constraint.startsWith("ex_franjas_")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", new FranjaSuperpuestaException().getMessage()));
        }
        LOG.error("Violación de integridad inesperada en reservas (constraint={})", constraint, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "No se pudo completar la operación."));
    }

    @ExceptionHandler(FranjaSuperpuestaException.class)
    public ResponseEntity<Map<String, String>> handleFranjaSuperpuesta(FranjaSuperpuestaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    /** Nombre de la constraint de Postgres, buscando la causa de Hibernate en la cadena. */
    private static String constraintVioladaDe(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            // Hibernate no extrae el nombre de una EXCLUDE (SQLState 23P01): null → mensaje.
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
        }
        // Sin la excepción de Hibernate (p. ej. JdbcTemplate): el mensaje de Postgres la nombra.
        String mensaje = ex.getMostSpecificCause().getMessage();
        java.util.regex.Matcher m = mensaje == null ? null
                : java.util.regex.Pattern.compile("constraint \"([^\"]+)\"").matcher(mensaje);
        return m != null && m.find() ? m.group(1) : null;
    }

    @ExceptionHandler(TarifaNoConfiguradaException.class)
    public ResponseEntity<Map<String, String>> handleSinTarifa(TarifaNoConfiguradaException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage()));
    }
}