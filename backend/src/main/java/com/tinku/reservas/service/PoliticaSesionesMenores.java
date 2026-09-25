package com.tinku.reservas.service;

import org.springframework.beans.factory.annotation.Value;
import com.tinku.reservas.port.VerificadorHabilitacionMenores;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * T-TES-10/DT7 — gate del piloto: único árbitro de si las sesiones con menores
 * (Solicitudes de Sesión y Reservas con beneficiario menor, US-2/US-3/US-4)
 * están habilitadas. Default {@code false}: durante el piloto no hay clases con
 * menores. Fail-closed: la flag solo pasa a true cuando cierran T-M3-06
 * (kill-switch en cliente) y T02 (CAP) — ver AGENTS §3.
 */
@Component
public class PoliticaSesionesMenores {

    private final boolean sesionesHabilitadas;
    private final VerificadorHabilitacionMenores habilitacion;

    public PoliticaSesionesMenores(
            @Value("${tinku.menores.sesiones-habilitadas:false}") boolean sesionesHabilitadas,
            VerificadorHabilitacionMenores habilitacion) {
        this.sesionesHabilitadas = sesionesHabilitadas;
        this.habilitacion = habilitacion;
    }

    /**
     * FR-ID-026 (T02): el piloto habilitado Y el Tutor con CAP aprobado y vigente. Lo llama
     * todo punto de M4 donde hay un menor de por medio (reserva directa, aprobación de
     * solicitud, solicitud nueva). Fail-closed.
     */
    public void validarClaseConMenor(UUID tutorId) {
        validarSesionesHabilitadas();
        if (!habilitacion.habilitadoParaMenores(tutorId)) {
            throw new TutorSinHabilitacionMenoresException();
        }
    }

    /**
     * Fail-closed: quien la llama ya determinó que la operación involucra a un
     * menor; con la flag apagada corta con {@link SesionesConMenoresDeshabilitadasException}.
     */
    public void validarSesionesHabilitadas() {
        if (!sesionesHabilitadas) {
            throw new SesionesConMenoresDeshabilitadasException();
        }
    }
}