package com.tinku.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Preferencia de MercadoPago generada para una Reserva (R2, V38). Es el ancla de la
 * conciliación: aunque el comprador no vuelva y el webhook se pierda, el barrido busca los
 * pagos por {@code external_reference} = id de la Reserva mientras {@code conciliadoAt} sea
 * null y no hayan pasado 48 hs (Tabla de Tiempos).
 */
@Entity
@Table(name = "preferencias_pago", schema = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class PreferenciaMp {

    @Id
    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Column(name = "preference_id", nullable = false, length = 100)
    private String preferenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "conciliado_at")
    private Instant conciliadoAt;

    @Column(name = "intentos_conciliacion", nullable = false)
    private int intentosConciliacion;

    @Column(name = "alertado_at")
    private Instant alertadoAt;
}
