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
 * implementación real contra la API de MP es {@link ReembolsoProveedorMercadoPago}
 * (Chunk M5-D, T-M5-07). Los reembolsos PARCIALES por disputa son un flujo manual
 * separado de M8 (T-M5-08, {@link ReembolsoParcialProveedor}) — no comparten código
 * con este port, para que ninguna regla automática termine haciendo un parcial por
 * error.
 */
public interface ReembolsoProveedor {

    void reembolsarTotal(Transaccion transaccion);
}