package com.tinku.admin.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tinku.identidad.dto.EspecialidadResponse;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.NivelEspecialidad;
import com.tinku.identidad.model.TipoCredencial;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fila de la cola de Credenciales pendientes (US-1, FR-ADM-001) — lo que el
 * Admin necesita para decidir: de quién es, qué documento, en qué intento está
 * y el plazo de la ventana de 48hs ({@code cicloEsperaHasta}). No se expone el
 * {@code archivoUrl} del documento (referencia interna del filesystem, minimización):
 * el archivo se ve por {@code GET /api/admin/moderacion/credenciales/{id}/archivo},
 * que sirve los bytes (AUD-007).
 * El repo de la cola hace {@code join fetch} del tutor (open-in-view: false).
 *
 * <p>ADR-M1-06 (solo con {@code tinku.especialidades.habilitado} encendido; apagado, estos campos
 * no se serializan): DNI del Tutor (es lo que piden los registros oficiales), datos declarados,
 * marca de nombre ({@code nombreCoincide}: false = no coincide; ausente = no se pudo comparar), el
 * paso a seguir según el tipo, hasta qué nivel respalda el tipo y las especialidades del Tutor
 * para elegir cuáles cubre.</p>
 */
public record CredencialColaResponse(
        UUID id,
        UUID tutorId,
        String tutorNombre,
        String tutorApellido,
        TipoCredencial tipoDocumento,
        EstadoCredencial estado,
        int numeroIntento,
        Instant cicloEsperaHasta,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String tutorDni,
        @JsonInclude(JsonInclude.Include.NON_NULL) String institucion,
        @JsonInclude(JsonInclude.Include.NON_NULL) String denominacion,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer anioEgreso,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean esDigital,
        @JsonInclude(JsonInclude.Include.NON_NULL) String enlaceVerificacion,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean nombreCoincide,
        @JsonInclude(JsonInclude.Include.NON_NULL) String pasoASeguir,
        @JsonInclude(JsonInclude.Include.NON_NULL) NivelEspecialidad nivelMaximoQueRespalda,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<EspecialidadResponse> especialidades) {

    public static CredencialColaResponse from(CredencialAcademica c) {
        return new CredencialColaResponse(
                c.getId(), c.getTutor().getId(), c.getTutor().getNombre(),
                c.getTutor().getApellido(), c.getTipoDocumento(), c.getEstado(),
                c.getNumeroIntento(), c.getCicloEsperaHasta(), c.getCreatedAt(),
                null, null, null, null, null, null, null, null, null, null);
    }

    /** ADR-M1-06: la tarjeta completa para verificar contra el registro oficial. */
    public static CredencialColaResponse conDatosDeVerificacion(CredencialAcademica c,
                                                                List<EspecialidadResponse> especialidades) {
        boolean digital = Boolean.TRUE.equals(c.getEsDigital());
        return new CredencialColaResponse(
                c.getId(), c.getTutor().getId(), c.getTutor().getNombre(),
                c.getTutor().getApellido(), c.getTipoDocumento(), c.getEstado(),
                c.getNumeroIntento(), c.getCicloEsperaHasta(), c.getCreatedAt(),
                c.getTutor().getDni(), c.getInstitucion(), c.getDenominacion(), c.getAnioEgreso(),
                c.getEsDigital(), c.getEnlaceVerificacion(), c.getNombreCoincide(),
                c.getTipoDocumento().pasoASeguir(digital),
                c.getTipoDocumento().nivelMaximoQueRespalda().orElse(null),
                especialidades);
    }
}
