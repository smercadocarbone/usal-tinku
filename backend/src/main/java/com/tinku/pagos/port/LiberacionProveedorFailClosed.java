package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.service.LiberacionNoDisponibleException;
import org.springframework.stereotype.Component;

/**
 * STUB fail-closed de {@link LiberacionProveedor}: la liberación efectiva al
 * Tutor todavía no está implementada (requiere el job de Chunk M5-C). Nunca
 * registra un {@code liberado} sin haber liberado — Chunk M5-C reemplaza este
 * bean por la implementación real.
 */
@Component
public class LiberacionProveedorFailClosed implements LiberacionProveedor {

    @Override
    public void liberarAlTutor(Transaccion transaccion) {
        throw new LiberacionNoDisponibleException();
    }
}