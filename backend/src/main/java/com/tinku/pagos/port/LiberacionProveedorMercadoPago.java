package com.tinku.pagos.port;

import com.tinku.pagos.service.CuentasMpService;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.service.MercadoPagoNoDisponibleException;
import org.springframework.stereotype.Component;

/**
 * Implementación REAL de {@link LiberacionProveedor} (Chunk M5-C). Con el modelo
 * A de ADR-M5-02 (preferencia creada con el token OAuth del Tutor) MercadoPago
 * reparte al capturar: la plata ya está en la cuenta del Tutor y NO hay
 * "liberación" que llamar. Las 24 hs son una ventana lógica en la que Tinku
 * todavía puede reembolsar con el token del Tutor; {@code liberado} = la ventana
 * cerró. Sin OAuth configurado (token de la plataforma) no hay reparto: el cobro
 * queda en la cuenta de Tinku. Lo que Tinku SÍ controla al vencerse {@code liberar_at} es
 * verificar contra el proveedor que el pago sigue {@code approved} (no revertido
 * ni cargado a contracargo) antes de registrar {@code liberado}.
 *
 * Fail-closed: si MercadoPago no responde (o el pago ya no está aprobado) lanza
 * la excepción con que el job de Chunk M5-C dispara el backoff de FR-PAG-007 —
 * jamás se marca {@code liberado} un pago que el provider no confirma.
 */
@Component
public class LiberacionProveedorMercadoPago implements LiberacionProveedor {

    private final MercadoPagoClient mercadopago;
    private final CuentasMpService cuentasMp;

    public LiberacionProveedorMercadoPago(MercadoPagoClient mercadopago, CuentasMpService cuentasMp) {
        this.mercadopago = mercadopago;
        this.cuentasMp = cuentasMp;
    }

    @Override
    public void liberarAlTutor(Transaccion transaccion) {
        MercadoPagoClient.PagoMercadoPago pago = mercadopago.getPago(transaccion.getMpPaymentId(),
                cuentasMp.tokenParaTransaccion(transaccion));
        if (!pago.aprobado()) {
            // Revertido / contracargo: no es liberable; va al backoff.
            throw new MercadoPagoNoDisponibleException();
        }
    }
}