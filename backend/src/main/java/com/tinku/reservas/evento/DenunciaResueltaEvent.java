package com.tinku.reservas.evento;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * {@code denuncia.resuelta} (T-M4-09, M9) — una Denuncia se resolvió con
 * sanción. STUB definido por el consumidor: M9 (aún no existe) será quien lo
 * publique cuando implemente FR-SEC-008/012 (Chunk M9-D, que reemplaza este
 * contrato mínimo); acá solo se declara el payload que M4 necesita para
 * cancelar las reservas futuras del sancionado con motivo {@code sancion}.
 *
 * M5 también consume este evento (Spec_M5, sección 2 — reanudar escrow,
 * reembolso o sanción definitiva, FR-PAG-004/011). Si M9-D agrega payload,
 * se documenta en Spec_M5 §2 antes de cambiar este contrato (AGENTS §4).
 */
public class DenunciaResueltaEvent extends ApplicationEvent {

    private final UUID denunciaId;
    private final UUID usuarioSancionadoId;

    public DenunciaResueltaEvent(Object source, UUID denunciaId, UUID usuarioSancionadoId) {
        super(source);
        this.denunciaId = denunciaId;
        this.usuarioSancionadoId = usuarioSancionadoId;
    }

    public UUID getDenunciaId() {
        return denunciaId;
    }

    public UUID getUsuarioSancionadoId() {
        return usuarioSancionadoId;
    }
}