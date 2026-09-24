package com.tinku.reservas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public PoliticaSesionesMenores(
            @Value("${tinku.menores.sesiones-habilitadas:false}") boolean sesionesHabilitadas) {
        this.sesionesHabilitadas = sesionesHabilitadas;
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