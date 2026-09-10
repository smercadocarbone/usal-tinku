package com.tinku.reputacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "senales_implicitas_tutor", schema = "reputacion")
@Getter
@Setter
@NoArgsConstructor
public class SenalesImplicitasTutor {

    @Id
    @Column(name = "tutor_id")
    private UUID tutorId;

    @Column(name = "puntualidad_promedio", nullable = false)
    private BigDecimal puntualidadPromedio = BigDecimal.ONE;

    @Column(name = "tasa_recontratacion", nullable = false)
    private BigDecimal tasaRecontratacion = BigDecimal.ZERO;

    @Column(name = "tasa_cancelacion_noshow", nullable = false)
    private BigDecimal tasaCancelacionNoshow = BigDecimal.ZERO;

    @Column(name = "tiempo_respuesta_promedio_min", nullable = false)
    private Integer tiempoRespuestaPromedioMin = 0;

    @Column(name = "sesiones_dictadas_total", nullable = false)
    private Integer sesionesDictadasTotal = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
