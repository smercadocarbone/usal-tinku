package com.tinku.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Mapeo {@code origen_modulo → rol_asignado} para el enrutamiento automático de
 * tickets (T-M8-05, Plan M8 §3.3). Es una tabla de config accionable sin
 * desplegar código: el endpoint de creación de tickets resuelve el rol con un
 * SELECT, no con lógica de negocio.
 */
@Entity
@Table(name = "mapeo_origen_rol", schema = "admin")
@Getter
@NoArgsConstructor
public class MapeoOrigenRol {

    @Id
    @Column(name = "origen_modulo", nullable = false, length = 60)
    private String origenModulo;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_asignado", nullable = false, length = 30)
    private RolAdmin rolAsignado;
}