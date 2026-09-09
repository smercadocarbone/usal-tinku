package com.tinku.pagos.service;

/**
 * No hay ningún {@code precios_referencia_regional} cargado para la provincia
 * (o la provincia llegó vacía) — T-M5-09, US-6. Se responde 404: la sugerencia
 * es no vinculante y opcional (FR-PAG-005), el Tutor puede configurar su precio
 * sin ella.
 */
public class ProvinciaSinPrecioReferenciaException extends RuntimeException {

    public ProvinciaSinPrecioReferenciaException(String provincia) {
        super("No hay precio de referencia regional para la provincia: " + provincia);
    }
}