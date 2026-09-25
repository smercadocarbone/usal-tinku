package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.model.PoliticaCancelacion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.service.ReservaService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva vista por uno de sus participantes.
 *
 * <p>UX-05 §4: además de los ids, suma lo que la pantalla necesita para no pedir
 * cada perfil por separado (nombres, duración) y las ACCIONES disponibles
 * calculadas acá, para que la regla de negocio no se duplique en el frontend:</p>
 * <ul>
 *   <li>{@code pagoVenceAt}: fin del plazo para pagar (FR-RES-020), solo en {@code pendiente_pago}.</li>
 *   <li>{@code puedePagar}: quien mira es el pagador y el plazo sigue vigente.</li>
 *   <li>{@code puedeCancelar}: mismas condiciones que {@code ReservaService.cancelar}
 *       (pagador o Tutor, {@code pendiente_pago}/{@code confirmada}) y la clase
 *       todavía no empezó.</li>
 *   <li>{@code cancelarReembolsaTotal}: si cancelar AHORA devuelve todo al pagador
 *       ({@link PoliticaCancelacion}); {@code null} si no puede cancelar.</li>
 * </ul>
 * Nombres: solo nombre y apellido de los participantes (nunca DNI ni email).
 */
public record ReservaResponse(UUID id, UUID pagadorId, UUID beneficiarioId, UUID tutorId,
                              Instant horario, BigDecimal precio, EstadoReserva estado,
                              MotivoCancelacion motivoCancelacion,
                              String tutorNombre, String tutorApellido,
                              String beneficiarioNombre, String beneficiarioApellido,
                              Integer duracionMinutos,
                              Instant pagoVenceAt,
                              boolean puedePagar,
                              boolean puedeCancelar,
                              Boolean cancelarReembolsaTotal,
                              Instant horarioFin,
                              boolean resumenContratado,
                              BigDecimal precioAdicionalResumen,
                              BigDecimal montoTotal) {

    public static ReservaResponse from(Reserva r, Usuario quienMira, Instant ahora) {
        boolean pendiente = r.getEstado() == EstadoReserva.PENDIENTE_PAGO;
        Instant vence = pendiente ? r.getCreatedAt().plus(ReservaService.TIMEOUT_PENDIENTE_PAGO) : null;
        UUID yo = quienMira == null ? null : quienMira.getId();
        boolean esPagador = yo != null && r.getPagador().getId().equals(yo);
        boolean esTutor = yo != null && r.getTutor().getId().equals(yo);
        boolean puedePagar = esPagador && pendiente && vence.isAfter(ahora);
        boolean puedeCancelar = (esPagador || esTutor)
                && (pendiente || r.getEstado() == EstadoReserva.CONFIRMADA)
                && r.getHorario().isAfter(ahora);
        Boolean reembolsaTotal = puedeCancelar
                ? (pendiente || PoliticaCancelacion.reembolsoTotal(r, yo, ahora))
                : null;
        Usuario beneficiario = r.getBeneficiario();
        return new ReservaResponse(r.getId(), r.getPagador().getId(), beneficiario.getId(),
                r.getTutor().getId(), r.getHorario(), r.getPrecio(), r.getEstado(),
                r.getMotivoCancelacion(),
                r.getTutor().getNombre(), r.getTutor().getApellido(),
                beneficiario.getNombre(), beneficiario.getApellido(),
                r.getDuracionMinutos(),
                vence, puedePagar, puedeCancelar, reembolsaTotal, r.getHorarioFin(),
                r.isResumenContratado(), r.getPrecioAdicionalResumen(), r.montoTotal());
    }
}
