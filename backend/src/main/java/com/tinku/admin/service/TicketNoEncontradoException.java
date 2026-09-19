package com.tinku.admin.service;

/** 404 — el ticket de soporte no existe. */
public class TicketNoEncontradoException extends RuntimeException {
    public TicketNoEncontradoException() {
        super("Ticket de soporte no encontrado.");
    }
}
