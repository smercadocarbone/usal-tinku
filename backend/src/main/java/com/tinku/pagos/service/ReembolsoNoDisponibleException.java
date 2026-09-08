package com.tinku.pagos.service;

/**
 * El reembolso total al Estudiante no se pudo ejecutar porque el proveedor no
 * está conectado (Chunk M5-D, T-M5-07). Fail-closed: un listener NUNCA registra
 * {@code reembolsado} sin haber reembolsado realmente.
 */
public class ReembolsoNoDisponibleException extends RuntimeException {

    public ReembolsoNoDisponibleException() {
        super("El reembolso total todavía no está disponible (Chunk M5-D).");
    }
}