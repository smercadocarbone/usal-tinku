package com.tinku.admin.web;

import com.tinku.pagos.model.EstadoReembolsoAdicional;
import com.tinku.pagos.model.Transaccion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila de la cola de reembolsos del adicional de resumen (R4, BR-PAG-11). */
public record ReembolsoAdicionalResponse(
        UUID id,
        UUID reservaId,
        BigDecimal monto,
        EstadoReembolsoAdicional estado,
        int intentos,
        String ultimoError,
        String nota,
        Instant reembolsadoAt) {

    public static ReembolsoAdicionalResponse from(Transaccion t) {
        return new ReembolsoAdicionalResponse(t.getId(), t.getReservaId(), t.getMontoAdicionalResumen(),
                t.getAdicionalReembolsoEstado(), t.getAdicionalReembolsoIntentos(), t.getAdicionalReembolsoError(),
                t.getAdicionalReembolsoNota(), t.getAdicionalReembolsadoAt());
    }
}
