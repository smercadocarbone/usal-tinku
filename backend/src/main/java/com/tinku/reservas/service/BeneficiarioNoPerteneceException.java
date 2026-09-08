package com.tinku.reservas.service;

/** El beneficiario indicado en una Reserva directa no es un menor a cargo del pagador (FR-ID-020). */
public class BeneficiarioNoPerteneceException extends RuntimeException {
    public BeneficiarioNoPerteneceException() {
        super("El beneficiario indicado no es un menor a cargo de esta cuenta.");
    }
}