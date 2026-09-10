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
 * Alerta de Seguridad del kill-switch (US-2, BR-KS-03): entidad separada de la
 * Denuncia, vive en la tabla de M3 ({@code aula.alertas_seguridad}, V8) — acá
 * se crea la entidad de solo-lectura/resolución que necesita M9. La suspensión
 * preventiva del Tutor ya la hizo M3; el Admin de Moderación y Seguridad solo
 * la resuelve (reactivar o sancionar) sin esperar descargo (FR-SEC-004).
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

    @Column(name = "rama", nullable = false, length = 10)
    private String rama;

    @Column(name = "detectado_id", nullable = false)
    private UUID detectadoId;

    @Column(name = "clip_url", length = 500)
    private String clipUrl;

    /** BR-KS-02: fin de la retención del clip (30 días desde la resolución, lo fija M9 al resolver). */
    @Column(name = "clip_retencion_hasta")
    private Instant clipRetencionHasta;

    @Column(nullable = false, length = 30)
    private String estado = ESTADO_PENDIENTE_REVISION;

    /** Descargo del Tutor como vía de apelación — nunca bloquea la resolución (FR-SEC-004). */
    @Column(name = "descargo_texto", length = 300)
    private String descargoTexto;

    @Column(name = "descargo_recibido_at")
    private Instant descargoRecibidoAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}