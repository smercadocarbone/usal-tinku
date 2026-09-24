package com.tinku.identidad.model;

public enum EstadoCuenta {
    ACTIVA,
    SUSPENDIDA,

    /** FASE2-06 / AUD-017 (ADR-M1-05): cuenta anonimizada en vez de DELETE.
     *  Requiere el CHECK de V27 (admite 'BAJA') y el filtro de listarMenores. */
    BAJA
}
