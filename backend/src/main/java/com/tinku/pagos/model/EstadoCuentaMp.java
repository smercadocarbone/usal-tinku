package com.tinku.pagos.model;

/** Estado de la conexión de MercadoPago de un Tutor (ADR-M5-02). Solo CONECTADA cobra. */
public enum EstadoCuentaMp {
    CONECTADA,
    REVOCADA,
    ERROR
}
