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
import java.util.UUID;

/**
 * Credencial Académica de un Tutor (US-4) — tabla {@code credenciales_academicas}
 * (V2). El Tutor la carga (T-M1-10); el panel Admin (M8) la aprueba o rechaza.
 *
 *  - {@code numeroIntento}: intento dentro del ciclo actual (1..3, FR-ID-008).
 *  - {@code cicloEsperaHasta}: si está en el futuro, el Tutor no puede subir
 *    una credencial nueva (FR-ID-012). La escalada 24h→48h→96h la administra
 *    {@code CredencialBackoffService} sobre {@code intentos_credencial} (V5).
 *
 * No se persiste el archivo: solo {@code archivoUrl} (minimización de datos,
 * Constitución Artículo V).
 */
@Entity
@Table(name = "credenciales_academicas", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class CredencialAcademica {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Usuario tutor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 30)
    private TipoCredencial tipoDocumento;

    @Column(name = "archivo_url", nullable = false, length = 500)
    private String archivoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCredencial estado = EstadoCredencial.PENDIENTE;

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
}
