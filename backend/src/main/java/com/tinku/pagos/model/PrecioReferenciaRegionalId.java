package com.tinku.pagos.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Clave compuesta de {@code pagos.precios_referencia_regional} — {@code provincia}
 * + {@code version} (V11, FR-ADM-007 de M8): acumular versiones garantiza que una
 * revisión trimestral ({\@code version} +1, nueva fila) nunca sobreescribe la
 * anterior — "nunca se sobreescribe la fila" del Plan M5 §1.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class PrecioReferenciaRegionalId implements Serializable {

    private String provincia;

    private Integer version;
}