package com.tinku.pagos.model;

/** R4 (BR-PAG-11): estado del reembolso del adicional de resumen. */
public enum EstadoReembolsoAdicional {
    /** Agendado o reintentando con backoff. */
    PENDIENTE,
    /** Se agotaron los reintentos: en la cola de Soporte Financiero. */
    FALLIDO,
    HECHO,
    /** Soporte lo devolvió por fuera (panel de MercadoPago) y lo registró. */
    RESUELTO_MANUAL
}
