package com.tinku.reputacion.service;

public class EstrellasInvalidasException extends RuntimeException {
    public EstrellasInvalidasException() {
        super("Las estrellas deben estar entre 1 y 5.");
    }
}