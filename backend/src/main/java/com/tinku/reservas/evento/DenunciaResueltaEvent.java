package com.tinku.reservas.evento;

import com.tinku.shared.ResolucionDenuncia;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * {@code denuncia.resuelta} (T-M4-09 stub de M4 + enriquecido en T-M9-04/M9). Al
 * resolver una Denuncia, M9 publica este evento enriquecido con lo que los dos
 * consumidores necesitan:
 * <ul>
 *   <li><b>M4</b> — {@code usuarioSancionadoId}: cancela las reservas futuras
 *       del sancionado con motivo {@code sancion} (FR-SEC-008/012). Guard: con
 *       {@code resolucion = infundada} no cancela nada.</li>
 *   <li><b>M5</b> — {@code reservaId} + {@code resolucion}: reanuda el escrow
 *       de ESA sesión puntual (FR-SEC-011), reembolsa (escalada/fundada) o
 *       re-cuenta la liberación (infundada).</li>
 * </ul>
 * Contrato documentado en Spec_M5 §2 (AGENTS §4) — acá solo se declara el
 * payload ({@code reservaId} es {@code null} en denuncias de perfil, sin sesión).
 */
public class DenunciaResueltaEvent extends ApplicationEvent {

    private final UUID denunciaId;
    private final UUID usuarioSancionadoId;
    private final UUID reservaId;
    private final ResolucionDenuncia resolucion;

    /** Constructor del stub original (T-M4-09): compatibilidad con el publisher
     * mínimo hasta que M9 enriquecí; {@code reservaId}/{@code resolucion} null
     * (M4 mantiene su comportamiento de cancelar; M5 no mueve dinero). */
    public DenunciaResueltaEvent(Object source, UUID denunciaId, UUID usuarioSancionadoId) {
        this(source, denunciaId, usuarioSancionadoId, null, null);
    }

    public DenunciaResueltaEvent(Object source, UUID denunciaId, UUID usuarioSancionadoId,
                                 UUID reservaId, ResolucionDenuncia resolucion) {
        super(source);
        this.denunciaId = denunciaId;
        this.usuarioSancionadoId = usuarioSancionadoId;
        this.reservaId = reservaId;
        this.resolucion = resolucion;
    }

    public UUID getDenunciaId() {
        return denunciaId;
    }

    public UUID getUsuarioSancionadoId() {
        return usuarioSancionadoId;
    }

    public UUID getReservaId() {
        return reservaId;
    }

    public ResolucionDenuncia getResolucion() {
        return resolucion;
    }
}