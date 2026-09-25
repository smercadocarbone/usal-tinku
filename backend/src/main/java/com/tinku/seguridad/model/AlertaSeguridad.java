package com.tinku.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Alerta de Seguridad del kill-switch (US-6/US-7, BR-KS-01 a BR-KS-04).
 * Entidad separada de Denuncia (BR-KS-03). M9 la recorre en su ventana de 12hs
 * (Tabla_Tiempos) y la resuelve sin esperar descargo.
 */
@Entity
@Table(name = "alertas_seguridad", schema = "aula")
@Getter
@Setter
@NoArgsConstructor
public class AlertaSeguridad {

    public static final String ESTADO_PENDIENTE_REVISION = "pendiente_revision";
    public static final String ESTADO_RESUELTA_REACTIVACION = "resuelta_reactivacion";
    public static final String ESTADO_RESUELTA_BAJA = "resuelta_baja";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sesionId;

    @Column(nullable = false, length = 10)
    private String rama;

    @Column(name = "detectado_id", nullable = false)
    private UUID detectadoId;

    @Column(name = "clip_url", length = 500)
    private String clipUrl;

    @Column(name = "clip_retencion_hasta")
    private Instant clipRetencionHasta;

    @Column(nullable = false, length = 30)
    private String estado = ESTADO_PENDIENTE_REVISION;

    /** Descargo del Tutor detectado como apelación — nunca bloquea la resolución (FR-SEC-004). */
    @Column(name = "descargo_texto", length = 300)
    private String descargoTexto;

    @Column(name = "descargo_recibido_at")
    private Instant descargoRecibidoAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
