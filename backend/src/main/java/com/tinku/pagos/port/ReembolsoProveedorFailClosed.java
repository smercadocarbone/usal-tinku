package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.service.ReembolsoNoDisponibleException;
import org.springframework.stereotype.Component;

/**
 * STUB fail-closed de {@link ReembolsoProveedor}: el reembolso total a Estudiantes
 * vía la API de MP con body vacío (FR-PAG-009) llega en Chunk M5-D (T-M5-07).
 * Nunca registra un {@code reembolsado} sin haber reembolsado — Chunk M5-D
 * reemplaza este bean por la implementación real.
 */
@Component
public class ReembolsoProveedorFailClosed implements ReembolsoProveedor {

    @Override
    public void reembolsarTotal(Transaccion transaccion) {
        throw new ReembolsoNoDisponibleException();
    }
}