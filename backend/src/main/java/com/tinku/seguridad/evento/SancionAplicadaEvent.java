package com.tinku.seguridad.evento;

import com.tinku.seguridad.model.OrigenSancion;
import com.tinku.seguridad.model.TipoSancion;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code sancion.aplicada} (T-M9-06, evento NUEVO de M9 — documentado en
 * Spec_M5 §2, AGENTS §4): se persiste una fila en {@code sanciones} y este
 * evento propaga su efecto a los módulos afectados, todos en la misma
 * transacción del publicador (Plan_M9 §2.5 — atómico: si un listener falla,
 * se aborta todo el caso). Los listeners viven en {@code com.tinku.seguridad}.
 * <ul>
 *   <li>M1: {@code estado_cuenta = suspendida} (definitiva/baneo) o job de
 *       reactivación a {@code vigenteHasta} (temporal).</li>
 *   <li>M2: {@code activo_para_matching = false} (exclusión del matching — el
 *       flag se lee de {@code usuarios}, ver {@code UsuarioRepository}).</li>
 *   <li>M4: cancela las reservas futuras del sancionado (como Tutor o como
 *       pagador), emitiendo {@code reserva.cancelada} que M5 reembolsa.</li>
 * </ul>
 * M5 reacciona a las consecuencias puntuales vía el constructor acordado
 * {@code denuncia.resuelta} (escrow de esa sesión) y a las futuras vía el
 * encadenamiento {@code reserva.cancelada} — FR-PAG-011/FR-SEC-012. No hay
 * fondos que mover por una sanción "desnuda"; si un día la sanción debe
 * tocar dinero directamente, se documenta acá antes (AGENTS §4).
 */
public class SancionAplicadaEvent extends ApplicationEvent {

    private final UUID sancionId;
    private final UUID usuarioSancionadoId;
    private final TipoSancion tipo;
    private final Integer diasSuspension;
    private final Instant vigenteHasta;
    private final OrigenSancion origen;

    public SancionAplicadaEvent(Object source, UUID sancionId, UUID usuarioSancionadoId,
                                TipoSancion tipo, Integer diasSuspension, Instant vigenteHasta,
                                OrigenSancion origen) {
        super(source);
        this.sancionId = sancionId;
        this.usuarioSancionadoId = usuarioSancionadoId;
        this.tipo = tipo;
        this.diasSuspension = diasSuspension;
        this.vigenteHasta = vigenteHasta;
        this.origen = origen;
    }

    public UUID getSancionId() {
        return sancionId;
    }

    public UUID getUsuarioSancionadoId() {
        return usuarioSancionadoId;
    }

    public TipoSancion getTipo() {
        return tipo;
    }

    public Integer getDiasSuspension() {
        return diasSuspension;
    }

    public Instant getVigenteHasta() {
        return vigenteHasta;
    }

    public OrigenSancion getOrigen() {
        return origen;
    }
}