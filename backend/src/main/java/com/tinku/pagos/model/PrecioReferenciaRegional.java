package com.tinku.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Precio de referencia regional por provincia (US-6, FR-PAG-005/006, Plan M5 §1).
 * Una fila por {@code (provincia, version)}: la revisión trimestral de M8
 * (FR-ADM-007) agrega una {@code version} nueva en vez de sobreescribir la
 * anterior, y la vigente es la de mayor {@code version} (ver
 * {@code PrecioReferenciaRegionalRepository#findFirstByProvinciaOrderByVersionDesc}).
 *
 * El valor es NO vinculante y se COPIA al perfil del Tutor (sin FK a esta fila,
 * FR-PAG-005): un Tutor que ya fijó su precio no se ve afectado por una versión
 * nueva (FR-PAG-006) — la sugerencia solo sirve al configurar el perfil por
 * primera vez.
 */
@Entity
@IdClass(PrecioReferenciaRegionalId.class)
@Table(name = "precios_referencia_regional", schema = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class PrecioReferenciaRegional {

    @Id
    @Column(nullable = false, length = 50)
    private String provincia;

    @Id
    @Column(nullable = false)
    private Integer version;

    /** Valor sugerido POR HORA de clase (D6, FASE2-01): misma unidad que la tarifa del Tutor. */
    @Column(name = "valor_sugerido", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorSugerido;

    @Column(name = "vigente_desde", nullable = false)
    private Instant vigenteDesde;
}