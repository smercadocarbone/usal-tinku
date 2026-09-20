package com.tinku.resumen.service;

/** Solo el tutor, el beneficiario/estudiante o el pagador de la Reserva que
 * originó la Sesión pueden consultar su resumen — nunca un tercero (403). */
public class ResumenNoPermitidoException extends RuntimeException {
    public ResumenNoPermitidoException() {
        super("Solo los participantes de la sesión pueden ver su resumen.");
    }
}
