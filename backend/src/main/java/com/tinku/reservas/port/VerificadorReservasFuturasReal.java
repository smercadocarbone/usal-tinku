package com.tinku.reservas.port;

import com.tinku.identidad.port.VerificadorReservasFuturas;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementación real del puerto {@code VerificadorReservasFuturas} (M4), que
 * M1 usa para la baja de menor (FR-ID-014, T-M1-12): cuenta las reservas del
 * menor (beneficiario) cuyo horario todavía no pasó y cuyo estado es activo —
 * {@code pendiente_pago}, {@code confirmada} o {@code en_curso}. Las finalizadas,
 * canceladas o no-show ya no importan para la baja.
 */
@Component
public class VerificadorReservasFuturasReal implements VerificadorReservasFuturas {

    private static final List<EstadoReserva> ESTADOS_ACTIVOS = List.of(
            EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA, EstadoReserva.EN_CURSO);

    private final ReservaRepository reservaRepo;

    public VerificadorReservasFuturasReal(ReservaRepository reservaRepo) {
        this.reservaRepo = reservaRepo;
    }

    @Override
    public long contarReservasFuturas(UUID menorId) {
        return reservaRepo.countByEstadoInAndHorarioAfterAndBeneficiario_Id(
                ESTADOS_ACTIVOS, Instant.now(), menorId);
    }
}