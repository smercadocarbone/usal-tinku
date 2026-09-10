package com.tinku.reputacion.service;

public class CalificacionNoPermitidaException extends RuntimeException {
    public CalificacionNoPermitidaException() {
        super("Solo el Tutor, el beneficiario o el pagador de la Sesion pueden calificarla.");
    }
}