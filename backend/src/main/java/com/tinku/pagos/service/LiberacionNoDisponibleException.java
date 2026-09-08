package com.tinku.pagos.service;

/**
 * La liberación al Tutor no se pudo ejecutar porque el proveedor no está
 * conectado (Chunk M5-C). Fail-closed: un listener NUNCA registra {@code liberado}
 * sin haber liberado realmente.
 */
public class LiberacionNoDisponibleException extends RuntimeException {

    public LiberacionNoDisponibleException() {
        super("La liberación de fondos al Tutor todavía no está disponible (Chunk M5-C).");
    }
}