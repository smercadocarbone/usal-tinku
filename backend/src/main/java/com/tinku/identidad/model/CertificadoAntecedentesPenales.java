package com.tinku.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Certificado de Antecedentes Penales de un Tutor (US-6) — tabla
 * {@code certificados_antecedentes_penales} (V6). El Tutor lo carga
 * (T-M1-15); el panel Admin (M8) lo revisa (T-M1-16); un job de Quartz lo
 * vence a los 12 meses de emisión (T-M1-17, FR-ID-025).
 *
 *  - {@code en_revision_legal} (BR-CAP-02): caso con antecedente fuera de la
 *    lista de rechazo automático o proceso en trámite — NUNCA se auto-resuelve.
 *  - No se persiste el binario, solo {@code archivoUrl} (Artículo V).
 */
@Entity
@Table(name = "certificados_antecedentes_penales", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class CertificadoAntecedentesPenales {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Usuario tutor;

    @Column(name = "archivo_url", nullable = false, length = 500)
    private String archivoUrl;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "vence_at", nullable = false)
    private LocalDate venceAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCap estado = EstadoCap.PENDIENTE;

    @Column(name = "tiene_antecedentes", nullable = false)
    private boolean tieneAntecedentes = false;

    @Column(name = "categoria_antecedente", length = 255)
    private String categoriaAntecedente;

    @Column(name = "numero_intento", nullable = false)
    private int numeroIntento = 1;

    @Column(name = "ciclo_espera_hasta")
    private Instant cicloEsperaHasta;

    @Column(name = "admin_revisor_id")
    private UUID adminRevisorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revisado_at")
    private Instant revisadoAt;

    // ---- ADR-M1-10: verificación de la firma digital con el validador oficial ----

    @Enumerated(EnumType.STRING)
    @Column(name = "firma_estado", nullable = false, length = 30)
    private EstadoFirmaCap firmaEstado = EstadoFirmaCap.NO_VERIFICADA;

    @Column(name = "firma_detalle", length = 500)
    private String firmaDetalle;

    @Column(name = "firma_firmante", length = 300)
    private String firmaFirmante;

    /** Hash del archivo enviado: el resultado solo vale si el validador informa el mismo. */
    @Column(name = "firma_sha256", length = 64)
    private String firmaSha256;

    @Column(name = "firma_solicitud_id", length = 120)
    private String firmaSolicitudId;

    @Column(name = "firma_intentos", nullable = false)
    private int firmaIntentos = 0;

    @Column(name = "firma_proximo_intento_at")
    private Instant firmaProximoIntentoAt;

    @Column(name = "firma_enviada_at")
    private Instant firmaEnviadaAt;

    @Column(name = "firma_verificada_at")
    private Instant firmaVerificadaAt;
}
