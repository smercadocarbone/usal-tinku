package com.tinku.pagos.port;

import com.tinku.pagos.service.ReembolsoNoDisponibleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * STUB fail-closed de {@link ReembolsoParcialProveedor} (T-M5-08): el flujo
 * MANUAL de reembolso parcial por disputa se ejecuta desde M8 y M8 todavía no
 * existe. Nunca registra un parcial pagado sin haberlo ejecutado — cuando M8
 * llegue, se reemplaza este bean por la implementación real (POST
 * /v1/payments/{id}/refunds con {@code amount}, diferencia de comisión
 * absorbida por Tinku, FR-PAG-010).
 */
@Component
public class ReembolsoParcialProveedorFailClosed implements ReembolsoParcialProveedor {

    @Override
    public void reembolsarParcial(String mpPaymentId, BigDecimal monto) {
        throw new ReembolsoNoDisponibleException();
    }
}