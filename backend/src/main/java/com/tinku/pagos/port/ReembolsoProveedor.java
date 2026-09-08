package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;

/**
 * Reembolso TOTAL al Estudiante (Plan M5 §3.3, FR-PAG-009): la llamada a la API
 * de MercadoPago siempre lleva el body VACÍO, lo que hace que MP reintegre además
 * su propia comisión — costo real cero para Tinku. Prohibido cualquier reembolso
 * parcial automático desde este módulo.
 *
 * Este port ES la función única de reembolso (punto crítico del Plan §6): todo el
 * código que necesite reembolsar pasa por acá, nunca reimplementa la llamada. La
 * implementación real contra la API de MP llega en Chunk M5-D (T-M5-07); hasta
 * entonces {@link ReembolsoProveedorFailClosed} falla ruidosamente en lugar de
 * registrar un "reembolsado" que nunca se ejecutó.
 */
public interface ReembolsoProveedor {

    void reembolsarTotal(Transaccion transaccion);
}